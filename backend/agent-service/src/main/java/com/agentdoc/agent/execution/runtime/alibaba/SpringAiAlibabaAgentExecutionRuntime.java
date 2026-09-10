package com.agentdoc.agent.execution.runtime.alibaba;

import com.agentdoc.agent.constant.AgentConstant;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitExceededException;
import com.agentdoc.agent.execution.audit.AgentExecutionModelCallAuditService;
import com.agentdoc.agent.execution.context.AgentRuntimeContext;
import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.execution.model.ModelCapabilities;
import com.agentdoc.agent.execution.model.ModelAdapterRegistry;
import com.agentdoc.agent.execution.model.ModelSamplingOptions;
import com.agentdoc.agent.execution.runtime.AgentExecutionCanceledException;
import com.agentdoc.agent.execution.runtime.AgentExecutionLimitExceededException;
import com.agentdoc.agent.execution.runtime.AgentExecutionRuntime;
import com.agentdoc.agent.execution.runtime.AgentRuntimeResult;
import com.agentdoc.agent.execution.runtime.AgentRuntimeType;
import com.agentdoc.agent.execution.runtime.ConditionalOnAgentRuntime;
import com.agentdoc.agent.execution.tool.ExecutionToolSession;
import com.agentdoc.agent.execution.tool.ExecutionToolSessionFactory;
import com.agentdoc.agent.execution.tool.TokenUsageEstimator;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionModelCallEntity;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 【桥接适配器实现】Spring‑AI Alibaba ReactAgent 运行时
 * 实现自研顶层SPI接口 {@link AgentExecutionRuntime}，对外提供统一执行契约。
 * 底层不手写Re‑Act循环，复用Spring‑AI Alibaba {@link ReactAgent} 完成多轮Agent推理。
 *
 * <p>本层承担的适配职责：
 * <ol>
 * <li>领域对象转换：我方实体(AgentEntity/ModelEntity/AgentTaskInputDTO) ↔ Spring‑AI SDK对象</li>
 * <li>横切防护：任务取消信号检测、执行超时、最大模型调用次数熔断、Token预算管控</li>
 * <li>Token用量采集：原始响应提取 + 缺失字段估算补齐，统一归集用量</li>
 * <li>流式增强：包装ChatModel，透出文本delta回调，流式chunk做消息拼接</li>
 * <li>异常翻译：SDK抛出的各种异常统一转换为我方业务异常体系向上抛出</li>
 * <li>返回值适配：SDK执行结果封装成我方统一DTO {@link AgentRuntimeResult}</li>
 * </ol>
 * </p>
 * <p>生效条件：配置 agent‑doc.agent.runtime.type=SPRING_AI_ALIBABA 才会创建该Bean</p>
 * <p>资源管理：通过{@link ExecutionToolSessionFactory}构建任务工具会话，try‑with‑resources自动释放MCP连接。</p>
 * <p>取消逻辑：组合外部取消信号 + 执行超时标记，多处轮询检测，触发后抛出{@link AgentExecutionCanceledException}终止任务。</p>
 */
@Component("springAiAlibabaAgentExecutionRuntime")
@ConditionalOnAgentRuntime(AgentRuntimeType.SPRING_AI_ALIBABA)
@Slf4j
public class SpringAiAlibabaAgentExecutionRuntime implements AgentExecutionRuntime {

    // Agent 配置加密解密服务
    private final AgentConfigCryptoService cryptoService;
    // 模型适配器注册中心
    private final ModelAdapterRegistry adapterRegistry;
    // Token 本地估算器
    private final TokenUsageEstimator tokenUsageEstimator;
    private final AgentExecutionModelCallAuditService modelCallAuditService;
    /** Skill资源与过滤后MCP工具的统一会话工厂；ObjectProvider做延迟获取，避免循环依赖 */
    private final ObjectProvider<ExecutionToolSessionFactory> toolSessionFactoryProvider;

    public SpringAiAlibabaAgentExecutionRuntime(AgentConfigCryptoService cryptoService,
                                                ModelAdapterRegistry adapterRegistry,
                                                TokenUsageEstimator tokenUsageEstimator,
                                                AgentExecutionModelCallAuditService modelCallAuditService,
                                                ObjectProvider<ExecutionToolSessionFactory>
                                                        toolSessionFactoryProvider) {
        this.cryptoService = cryptoService;
        this.adapterRegistry = adapterRegistry;
        this.tokenUsageEstimator = tokenUsageEstimator;
        this.modelCallAuditService = modelCallAuditService;
        this.toolSessionFactoryProvider = toolSessionFactoryProvider;
    }

    /**
     * 同步阻塞执行Agent任务，无流式增量输出
     *
     * @param context        已经固化完成的Agent运行时上下文
     * @param cancelRequested 外部任务取消信号断言
     * @return Agent标准化执行结果
     */
    @Override
    public AgentRuntimeResult execute(AgentRuntimeContext context, BooleanSupplier cancelRequested) {
        return executeInternal(context, cancelRequested, ignored -> { }, false);
    }

    /**
     * 执行Agent任务，开启流式输出，向外推送文本增量片段
     *
     * @param context        已经固化完成的Agent运行时上下文
     * @param cancelRequested 外部任务取消信号断言
     * @param onTextDelta    文本增量回调；允许null，内部兜底为空回调
     * @return Agent标准化执行结果
     */
    @Override
    public AgentRuntimeResult execute(AgentRuntimeContext context, BooleanSupplier cancelRequested,
                                      Consumer<String> onTextDelta) {
        return executeInternal(context, cancelRequested, onTextDelta == null ? ignored -> { } : onTextDelta, true);
    }

    /**
     * 内部统一执行入口：组装工具会话和模型适配上下文并执行 ReactAgent。
     *
     * @param context        固定的 Agent 执行上下文
     * @param cancelRequested 外部取消信号
     * @param onTextDelta    流式文本增量回调
     * @param streaming      true‑流式模式；false‑同步非流式模式
     * @return 标准化Agent执行结果
     * @throws IllegalStateException               工具会话工厂缺失
     * @throws AgentExecutionCanceledException      任务被取消（外部信号/超时）
     * @throws AgentExecutionLimitExceededException 模型调用次数超限
     * @throws RuntimeException                    模型调用、SDK内部异常
     */
    private AgentRuntimeResult executeInternal(AgentRuntimeContext context,
                                                BooleanSupplier cancelRequested,
                                                Consumer<String> onTextDelta, boolean streaming) {
        // 标记是否执行超时，timeout超时算子会置为true
        AtomicBoolean timedOut = new AtomicBoolean();

        // 组合最终取消条件：外部取消信号 OR 执行超时，两处任意一个触发即判定任务取消
        BooleanSupplier executionCanceled = () -> timedOut.get() || cancelRequested.getAsBoolean();

        // 执行前快速校验：任务已经处于取消状态直接抛异常退出
        requireNotCanceled(executionCanceled);

        // 根据模型实体获取模型适配器，同时校验模型必须支持工具调用能力
        ModelAdapter adapter = adapterRegistry.require(context.model(), ModelCapabilities.requiredToolCalling());

        // token预算优先级：task入参 > agent配置；入参为null才取agent上配置的预算
        Long tokenBudget = context.taskInput().tokenBudget() == null
                ? context.agent().getTokenBudget() : context.taskInput().tokenBudget();

        // 实例化桥接控制器：维护迭代计数、token预算、取消检测状态
        AlibabaRuntimeControl control = new AlibabaRuntimeControl(context.agent(), tokenBudget,
                executionCanceled, tokenUsageEstimator);

        // 打开任务scope的MCP工具资源；try‑with‑resources保证执行结束自动关闭MCP连接
        ExecutionToolSessionFactory toolSessionFactory = toolSessionFactoryProvider.getIfAvailable();
        if (toolSessionFactory == null) {
            throw new IllegalStateException("Skill 工具会话工厂未配置");
        }
        try (ExecutionToolSession tools = toolSessionFactory.open(context, executionCanceled)) {
            ModelSamplingOptions samplingOptions = ModelSamplingOptions.from(context.model());

            // 组装模型适配器上下文：解密api‑key、最大输出token、MCP工具回调列表
            ModelAdapterContext adapterContext = new ModelAdapterContext(
                    context.agent(), context.model(),
                    cryptoService.decrypt(context.model().getEncryptedApiKey()),
                    modelMaxOutputTokens(context.model()), samplingOptions.temperature(),
                    samplingOptions.topP(), tools.callbacks())
                    .withExecutionId(context.executionId());

            // 获取缓存好的ChatModel实例（由ModelAdapter做封装）
            ChatModel chatModel = adapter.cachedChatModel(adapterContext);
            // 用量收集器：接收ChatResponse，提取+估算token用量，回传给control做统计
            AlibabaRuntimeUsageCollector usage = new AlibabaRuntimeUsageCollector(control, adapter);

            // 【装饰器】包装原生ChatModel，植入横切逻辑：模型调用前后拦截、用量采集、流式回调、取消校验
            ObservingChatModel observingModel = new ObservingChatModel(chatModel, adapterContext, tools.callbacks(),
                    control, usage, streaming ? onTextDelta : ignored -> { }, modelCallAuditService,
                    new AtomicInteger());

            // 构建Spring‑AI Alibaba原生ReactAgent
            ReactAgent reactAgent = ReactAgent.builder()
                    // 给agent实例一个标识名称，便于日志排查，id为null使用unknown占位
                    .name("agent-" + safe(context.agent().getId()) + "-execution-"
                            + safe(context.taskInput().workbenchTaskId()))
                    // 使用我们装饰之后的ChatModel，而不是原生model
                    .model(observingModel)
                    // 构建chatOptions，临时剥离工具回调，options只携带模型参数
                    .chatOptions(adapter.chatOptions(adapterContext.withToolCallbacks(List.of())))
                    .systemPrompt(context.systemPrompt())
                    // 注入MCP工具回调集合给ReactAgent
                    .tools(tools.callbacks())
                    // 挂载模型调用次数限制钩子，防止无限循环调用模型
                    .hooks(ModelCallLimitHook.builder()
                            .runLimit(modelCallLimit(control.maxIterations()))
                            .exitBehavior(ModelCallLimitHook.ExitBehavior.ERROR)
                            .build())
                    .build();
            try {
                // 使用Mono包装执行逻辑，调度到boundedElastic线程池；设置整体执行超时时间
                AssistantMessage result = Mono.fromCallable(() -> streaming
                                // 流式模式：调用自定义streamReactAgent消费ReactAgent图流
                                ? streamReactAgent(reactAgent, observingModel, context.instruction(),
                                control, context.taskInput())
                                // 同步模式：直接调用reactAgent.call执行
                                : reactAgent.call(context.instruction()))

                        // 切换到boundedElastic，避免阻塞http/webflux主线程
                        .subscribeOn(Schedulers.boundedElastic())
                        // 设置agent整体执行超时，超时后Mono抛出TimeoutException
                        .timeout(Duration.ofSeconds(timeoutSeconds(context.agent())))
                        // 捕获超时异常，标记timedOut为true，上层会识别该标记为任务取消
                        .doOnError(TimeoutException.class, ignored -> timedOut.set(true))
                        // 阻塞等待执行完成，这里是同步等待Reactor执行结果
                        .block();

                // 执行完毕再做一次取消校验，防止执行完成瞬间收到取消信号
                control.checkCanceled();

                // 将SDK返回结果，封装成我方统一返回DTO向上返回
                return new AgentRuntimeResult(result.getText(), usage.total());
            } catch (RuntimeException exception) {
                // 1. 处理超时场景：真实TimeoutException 或者 timedOut标记被置位
                TimeoutException timeout = findCause(exception, TimeoutException.class);
                if (timeout != null || timedOut.get()) {
                    // 包装为桥接层内部异常，交给adapter翻译后向外抛出
                    throw adapter.translateForRuntime(adapterContext,
                            new ModelInvocationException(timeout == null
                                    ? new TimeoutException("Agent 执行超时") : timeout));
                }

                // 2. 识别任务被取消异常，直接原样向上抛出
                AgentExecutionCanceledException cancellation = findCause(exception,
                        AgentExecutionCanceledException.class);
                if (cancellation != null)
                    throw cancellation;

                // 3. 识别SDK的模型调用次数超限，转换为我方业务异常AgentExecutionLimitExceededException
                ModelCallLimitExceededException limitException = findCause(exception,
                        ModelCallLimitExceededException.class);
                if (limitException != null) {
                    throw new AgentExecutionLimitExceededException(control.maxIterations());
                }

                // 4. 桥接层内部模型调用异常，交由ModelAdapter做错误翻译
                ModelInvocationException modelException = findCause(exception, ModelInvocationException.class);
                if (modelException != null)
                    throw adapter.translateForRuntime(adapterContext, modelException);

                // 其余未知异常直接透传
                throw exception;
            }
        }
    }

    /**
     * 获取agent配置的执行超时秒数；null使用全局默认常量
     */
    private int timeoutSeconds(AgentEntity agent) {
        return agent.getExecutionTimeoutSeconds() == null
                ? AgentConstant.DEFAULT_EXECUTION_TIMEOUT_SECONDS
                : agent.getExecutionTimeoutSeconds();
    }

    /**
     * 解析模型最大输出token，做Long→Int安全转换，防止溢出
     */
    private Integer modelMaxOutputTokens(ModelEntity model) {
        Long limit = model.getMaxOutputTokens();
        return limit == null ? null : Math.toIntExact(Math.min(limit, Integer.MAX_VALUE));
    }

    /**
     * 执行前置校验：已经取消，直接抛出取消异常
     */
    private void requireNotCanceled(BooleanSupplier cancelRequested) {
        if (cancelRequested.getAsBoolean()) throw new AgentExecutionCanceledException();
    }

    /**
     * 空安全toString，null返回"unknown"，用于日志、线程名
     */
    private String safe(Object value) { return value == null ? "unknown" : value.toString(); }

    /**
     * 适配Spring‑AI Alibaba ModelCallLimitHook计数逻辑
     * hook内部runLimit含义：允许执行runLimit次；所以maxIterations需要+1做边界兼容
     */
    static int modelCallLimit(int maxIterations) {
        return maxIterations == Integer.MAX_VALUE ? Integer.MAX_VALUE : maxIterations + 1;
    }

    /**
     * ReactAgent流式模式执行入口
     * 消费Agent编译图的stream数据流；每一步流元素到来检测一次取消；
     * blockLast阻塞直到流结束；最终从ObservingChatModel拿到拼接完成的AssistantMessage
     */
    private AssistantMessage streamReactAgent(ReactAgent agent, ObservingChatModel observingModel,
                                              String instruction,
                                              AlibabaRuntimeControl control, AgentTaskInputDTO input) {
        agent.getCompiledGraph()
                .stream(Map.of("messages", List.of(new UserMessage(instruction))),
                        RunnableConfig.builder().threadId("execution-" + safe(input.workbenchTaskId())).build())
                // 每一轮图执行，都检测任务是否被取消
                .doOnNext(ignored -> control.checkCanceled())
                // 把嵌套的取消异常解包，让异常链路干净
                .onErrorMap(SpringAiAlibabaAgentExecutionRuntime::unwrapCancellation)
                // 阻塞消费完整流，直到流结束
                .blockLast();

        // 从装饰器缓存取出完整的assistant消息
        AssistantMessage result = observingModel.lastAssistant();
        if (result == null)
            throw new IllegalStateException("ReactAgent 流式执行未返回最终回答");
        return result;
    }

    /**
     * 【ChatModel装饰器】ObservingChatModel
     * 使用装饰器模式包装原生ChatModel，对call/stream做增强拦截。
     * 职责：
     * 1. 模型调用前后触发control.beforeModel / afterModelFailure 做计数、取消、预算校验
     * 2. 把ChatResponse交给usage收集器处理token用量
     * 3. 流式模式下，向外emitTextDelta推送文本片段
     * 4. 缓存最后一条AssistantMessage，供流式模式执行结束后拿完整结果
     */
    private static final class ObservingChatModel implements ChatModel {
        /** 被代理的原生ChatModel实例 */
        private final ChatModel delegate;
        // 模型适配器上下文
        private final ModelAdapterContext context;
        /** 当前任务可用的全部MCP工具回调列表 */
        private final List<ToolCallback> tools;
        /** 桥接控制器，提供beforeModel、取消检测等能力 */
        private final AlibabaRuntimeControl control;
        /** token用量收集器 */
        private final AlibabaRuntimeUsageCollector usage;
        /** 文本增量回调，流式输出时推送片段 */
        private final Consumer<String> onTextDelta;
        private final AgentExecutionModelCallAuditService modelCallAuditService;
        private final AtomicInteger modelCallSequence;
        /** 原子引用保存最后一轮模型返回的AssistantMessage，流式模式需要靠它拿完整结果 */
        private final AtomicReference<AssistantMessage> lastAssistant = new AtomicReference<>();

        private ObservingChatModel(ChatModel delegate, ModelAdapterContext context, List<ToolCallback> tools,
                                   AlibabaRuntimeControl control, AlibabaRuntimeUsageCollector usage,
                                   Consumer<String> onTextDelta,
                                   AgentExecutionModelCallAuditService modelCallAuditService,
                                   AtomicInteger modelCallSequence) {
            this.delegate = delegate;
            this.context = context;
            this.tools = tools;
            this.control = control;
            this.usage = usage;
            this.onTextDelta = onTextDelta;
            this.modelCallAuditService = modelCallAuditService;
            this.modelCallSequence = modelCallSequence;
        }

        /**
         * 同步模型调用拦截
         */
        @Override
        public ChatResponse call(Prompt prompt) {
            // 模型调用前：校验取消、预算、迭代次数
            control.beforeModel();
            AgentExecutionModelCallEntity modelCall;
            try {
                modelCall = modelCallAuditService.start(context, modelCallSequence.incrementAndGet(),
                        prompt.getInstructions(), false);
            } catch (RuntimeException exception) {
                control.afterModelFailure();
                throw exception;
            }
            ChatResponse response;
            try {
                // 委托给底层真实ChatModel执行调用
                response = delegate.call(prompt);
            } catch (RuntimeException exception) {
                // 模型调用发生异常，清理inFlight标记
                control.afterModelFailure();
                finishFailed(modelCall, exception);
                // 如果是任务取消异常直接向上抛，不需要包装
                if (exception instanceof AgentExecutionCanceledException)
                    throw exception;
                // 其他异常包装为桥接内部模型调用异常
                throw new ModelInvocationException(exception);
            }
            // 正常返回，处理用量采集、推送文本delta
            try {
                accept(prompt, response);
            } catch (RuntimeException exception) {
                finishFailed(modelCall, exception);
                throw exception;
            }
            finishSucceeded(modelCall, response);
            return response;
        }

        /**
         * 流式模型调用拦截
         * 返回Flux<ChatResponse>，每一个元素是一个流式chunk片段
         */
        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            // 流式请求发起前，执行前置校验
            control.beforeModel();
            AgentExecutionModelCallEntity modelCall;
            try {
                modelCall = modelCallAuditService.start(context, modelCallSequence.incrementAndGet(),
                        prompt.getInstructions(), true);
            } catch (RuntimeException exception) {
                control.afterModelFailure();
                throw exception;
            }
            // 流式chunk累加器，用于流结束后拼装完整ChatResponse，用于token用量统计
            StreamResponseAccumulator accumulator = new StreamResponseAccumulator();
            // 保证completeStream只执行一次，防止complete/error/cancel多次触发重复统计用量
            AtomicBoolean finalized = new AtomicBoolean();
            AtomicBoolean auditFinalized = new AtomicBoolean();
            try {
                Flux<ChatResponse> source = delegate.stream(prompt).onErrorMap(exception ->
                        // 把非桥接异常统一包装为ModelInvocationException，保留特定异常透传
                        exception instanceof AgentExecutionCanceledException
                                || exception instanceof ModelInvocationException
                                ? exception : new ModelInvocationException(exception));
                return source.doOnNext(response -> {
                    // 每收到一块chunk，就检测一次任务取消
                    control.checkCanceled();
                    // 把chunk加入累加器
                    accumulator.accept(response);
                    // 向外推送文本片段
                    emitTextDelta(response);
                }).doOnComplete(() -> {
                    // 流正常完成：拼装完整response，交给用量收集器
                    completeStream(prompt, accumulator, false, finalized);
                    if (auditFinalized.compareAndSet(false, true)) {
                        if (accumulator.hasResponse()) {
                            finishSucceeded(modelCall, accumulator.response());
                        } else {
                            finishFailed(modelCall, new IllegalStateException("模型流式调用未返回执行结果"));
                        }
                    }
                }).doOnError(exception -> {
                    // 流异常：如果异常根因是任务取消，按取消分支处理用量统计
                    if (findCause(exception, AgentExecutionCanceledException.class) != null) {
                        completeStream(prompt, accumulator, true, finalized);
                    }
                    if (auditFinalized.compareAndSet(false, true)) {
                        finishFailed(modelCall, exception);
                    }
                })
                // 无论成功、失败、取消，最终都执行；CANCEL信号代表上游主动取消订阅
                .doFinally(signal -> {
                    if (signal == SignalType.CANCEL) {
                        completeStream(prompt, accumulator, true, finalized);
                        if (auditFinalized.compareAndSet(false, true)) {
                            finishFailed(modelCall, new AgentExecutionCanceledException());
                        }
                    }
                    // 清理模型正在调用标记
                    control.afterModelFailure();
                });
            } catch (RuntimeException exception) {
                control.afterModelFailure();
                finishFailed(modelCall, exception);
                throw new ModelInvocationException(exception);
            }
        }

        private void finishSucceeded(AgentExecutionModelCallEntity modelCall, ChatResponse response) {
            try {
                modelCallAuditService.succeed(modelCall, response);
            } catch (RuntimeException exception) {
                log.error("模型调用已成功但结束审计失败: executionId={}, auditId={}, sequence={}",
                        context.executionId(), modelCall.getId(), modelCall.getSequenceNo(), exception);
            }
        }

        private void finishFailed(AgentExecutionModelCallEntity modelCall, Throwable exception) {
            try {
                modelCallAuditService.fail(modelCall, exception.getClass().getSimpleName());
            } catch (RuntimeException auditException) {
                log.error("模型调用失败且结束审计失败: executionId={}, auditId={}, sequence={}",
                        context.executionId(), modelCall.getId(), modelCall.getSequenceNo(), auditException);
            }
        }

        @Override public ChatOptions getDefaultOptions() {
            return delegate.getDefaultOptions();
        }

        /**
         * 获取缓存的最后一条模型返回消息，流式模式执行结束后拿完整回答
         */
        private AssistantMessage lastAssistant() {
            return lastAssistant.get();
        }

        /**
         * 流式流结束统一处理：拼装完整response，调用用量收集器；finalized保证只执行一次
         * @param canceled true=任务被取消；false=正常结束
         */
        private void completeStream(Prompt prompt, StreamResponseAccumulator accumulator, boolean canceled,
                                     AtomicBoolean finalized) {
            // 没有收到任何chunk 或者 已经执行过本方法，直接跳过
            if (!accumulator.hasResponse() || !finalized.compareAndSet(false, true)) return;
            ChatResponse response = accumulator.response();
            if (canceled)
                // 取消场景：调用afterModelCanceled，记录用量，但跳过部分业务校验
                usage.acceptAfterCancellation(response, prompt.getInstructions(), tools);
            else
                // 正常完成：正常记录用量并触发预算、取消校验
                usage.accept(response, prompt.getInstructions(), tools);

            // 缓存完整assistant消息
            lastAssistant.set(response.getResult().getOutput());
        }

        /**
         * 同步模式调用成功后：采集用量、缓存消息、推送文本delta
         */
        private void accept(Prompt prompt, ChatResponse response) {
            usage.accept(response, prompt.getInstructions(), tools);
            emitTextDelta(response);
        }

        /**
         * 从response提取文本片段，调用onTextDelta向外推送；空文本跳过
         */
        private void emitTextDelta(ChatResponse response) {
            if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                AssistantMessage output = response.getResult().getOutput();
                lastAssistant.set(output);
                if (output.getText() != null && !output.getText().isEmpty()) onTextDelta.accept(output.getText());
            }
        }
    }

    /**
     * 递归遍历异常cause链，找到第一个匹配type的异常实例，找不到返回null
     */
    private static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getCause();
        }
        return null;
    }

    /**
     * 解包异常：如果cause链包含AgentExecutionCanceledException，直接抛出该异常，否则原样返回
     */
    static Throwable unwrapCancellation(Throwable exception) {
        AgentExecutionCanceledException cancellation = findCause(exception,
                AgentExecutionCanceledException.class);
        return cancellation == null ? exception : cancellation;
    }

    /**
     * 【桥接层内部异常标记】
     * 只在SpringAiAlibabaAgentExecutionRuntime内部流转，不会直接向上抛给上层业务；
     * 交给ModelAdapter.translateForRuntime做二次翻译之后再向外抛出。
     */
    private static final class ModelInvocationException extends RuntimeException {
        private ModelInvocationException(Throwable cause) {
            super("模型调用失败", cause);
        }
    }

    /**
     * 流式响应chunk累加器
     * Spring‑AI流式返回会把一条完整AssistantMessage拆成多个ChatResponse(chunk)
     * 每个chunk只携带部分text、部分toolCall参数片段。
     * 本类作用：
     * 1. 拼接text文本片段
     * 2. 合并同一个id的toolCall的arguments片段
     * 3. 保留metadata信息
     * 4. 组装回一条完整的ChatResponse，用于token用量统计（token统计需要完整响应）
     */
    private static final class StreamResponseAccumulator {
        /** 累积拼接模型输出文本 */
        private final StringBuilder text = new StringBuilder();
        /** key: toolCall.id，value: 不断merge的toolCall对象，用于合并分段的arguments */
        private final Map<String, AssistantMessage.ToolCall> toolCalls = new LinkedHashMap<>();
        /** 响应顶层元数据 */
        private ChatResponseMetadata responseMetadata;
        /** generation级别的元数据 */
        private ChatGenerationMetadata generationMetadata;
        /** 是否至少收到过一块有效chunk */
        private boolean received;

        /**
         * 接收一块流式chunk，把内容累积到内部字段
         */
        private void accept(ChatResponse response) {
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) return;
            received = true;

            // 保存元数据
            if (response.getMetadata() != null)
                responseMetadata = response.getMetadata();
            generationMetadata = response.getResult().getMetadata();
            AssistantMessage output = response.getResult().getOutput();

            // 追加文本片段
            if (output.getText() != null)
                text.append(output.getText());

            // 合并toolCall：同一个id做arguments拼接
            for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                AssistantMessage.ToolCall previous = toolCalls.get(toolCall.id());
                toolCalls.put(toolCall.id(), previous == null ? toolCall : merge(previous, toolCall));
            }
        }

        /** 是否接收到过有效chunk */
        private boolean hasResponse() {
            return received;
        }

        /**
         * 合并两段toolCall；type/name优先取新chunk非null值；arguments直接字符串拼接
         * 流式场景toolCall的arguments(json字符串)会被分片下发
         */
        private AssistantMessage.ToolCall merge(AssistantMessage.ToolCall previous,
                                                AssistantMessage.ToolCall current) {
            return new AssistantMessage.ToolCall(previous.id(),
                    current.type() == null ? previous.type() : current.type(),
                    current.name() == null ? previous.name() : current.name(),
                    safe(previous.arguments()) + safe(current.arguments()));
        }

        /**
         * 将全部累积完成的chunk，重建为一条完整ChatResponse对象
         */
        private ChatResponse response() {
            if (!received)
                throw new IllegalStateException("模型流式调用未返回执行结果");
            // 构建完整AssistantMessage
            AssistantMessage output = AssistantMessage.builder().content(text.toString())
                    .toolCalls(new ArrayList<>(toolCalls.values())).build();
            Generation generation = generationMetadata == null
                    ? new Generation(output) : new Generation(output, generationMetadata);
            return responseMetadata == null
                    ? new ChatResponse(List.of(generation))
                    : new ChatResponse(List.of(generation), responseMetadata);
        }

        /** null安全字符串，null返回空字符串，用于arguments拼接，避免null+字符串变成nullxxx */
        private String safe(String value) { return value == null ? "" : value; }
    }
}
