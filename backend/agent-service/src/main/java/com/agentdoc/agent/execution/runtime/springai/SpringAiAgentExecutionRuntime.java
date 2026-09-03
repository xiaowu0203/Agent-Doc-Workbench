package com.agentdoc.agent.execution.runtime.springai;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.execution.context.AgentRuntimeContext;
import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.execution.model.ModelAdapterRegistry;
import com.agentdoc.agent.execution.model.ModelCapabilities;
import com.agentdoc.agent.execution.model.ModelSamplingOptions;
import com.agentdoc.agent.execution.runtime.AgentExecutionCanceledException;
import com.agentdoc.agent.execution.runtime.AgentExecutionRuntime;
import com.agentdoc.agent.execution.runtime.AgentRuntimeResult;
import com.agentdoc.agent.execution.runtime.AgentRuntimeType;
import com.agentdoc.agent.execution.runtime.ConditionalOnAgentRuntime;
import com.agentdoc.agent.execution.tool.ExecutionToolSession;
import com.agentdoc.agent.execution.tool.ExecutionToolSessionFactory;
import com.agentdoc.agent.execution.tool.ProviderNeutralToolLoop;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 基于Spring‑AI实现的Agent运行时 {@link AgentExecutionRuntime}
 * <p>
 * 职责：建立MCP客户端连接、选择模型适配器、执行LLM+MCP工具调用完整会话；
 * 处理取消信号、最大工具迭代次数、Token预算上限、API密钥解密、MCP HTTP传输；
 * 不操作数据库，纯内存运行时，输出 {@link AgentRuntimeResult} 返回上层业务服务。
 * </p>
 * <p>
 * 取消机制：多处调用 requireNotCanceled() 轮询外部传入的cancelRequested回调，
 * 检测到取消标记立刻抛出 {@link AgentExecutionCanceledException} 终止执行。
 * </p>
 * <p>
 * 会话资源管理：通过{@link ExecutionToolSessionFactory}构建任务工具会话，
 * 使用try‑with‑resources保证MCP会话无论正常/异常都能关闭释放连接。
 * </p>
 */
@Component("customAgentExecutionRuntime")
@ConditionalOnAgentRuntime(AgentRuntimeType.CUSTOM)
public class SpringAiAgentExecutionRuntime implements AgentExecutionRuntime {
    /** 配置加密解密服务，解密模型存储的encryptedApiKey */
    private final AgentConfigCryptoService cryptoService;
    /** 模型适配器注册表，隔离不同厂商/协议的 SDK 差异 */
    private final ModelAdapterRegistry adapterRegistry;
    /** 厂商无关工具循环，统一控制工具执行和多轮模型调用 */
    private final ProviderNeutralToolLoop toolLoop;
    /** Skill资源与过滤后MCP工具的统一会话工厂；ObjectProvider做延迟获取，避免循环依赖 */
    private final ObjectProvider<ExecutionToolSessionFactory> toolSessionFactoryProvider;

    public SpringAiAgentExecutionRuntime(AgentConfigCryptoService cryptoService,
                                         ModelAdapterRegistry adapterRegistry,
                                         ProviderNeutralToolLoop toolLoop,
                                         ObjectProvider<ExecutionToolSessionFactory> toolSessionFactoryProvider) {
        this.cryptoService = cryptoService;
        this.adapterRegistry = adapterRegistry;
        this.toolLoop = toolLoop;
        this.toolSessionFactoryProvider = toolSessionFactoryProvider;
    }

    /**
     * 同步阻塞执行Agent任务，无流式增量输出
     * @param context        已经固化完成的Agent运行时上下文
     * @param cancelRequested 任务取消信号断言，轮询判断是否中断任务
     * @return Agent标准化执行结果对象
     */
    @Override
    public AgentRuntimeResult execute(AgentRuntimeContext context, BooleanSupplier cancelRequested) {
        return executeInternal(context, cancelRequested, ignored -> { }, false);
    }

    /**
     * 执行Agent任务，开启流式输出，向外推送文本增量片段
     *
     * @param context        已经固化完成的Agent运行时上下文
     * @param cancelRequested 任务取消信号断言，轮询判断是否中断任务
     * @param onTextDelta    文本增量回调，接收模型实时输出片段
     * @return Agent标准化执行结果对象
     */
    @Override
    public AgentRuntimeResult execute(AgentRuntimeContext context, BooleanSupplier cancelRequested,
                                      Consumer<String> onTextDelta) {
        return executeInternal(context, cancelRequested, onTextDelta, true);
    }

    /**
     * 内部统一执行入口：组装工具会话和模型适配上下文并执行工具循环。
     * @param context        固定的Agent执行上下文
     * @param cancelRequested 外部取消信号断言
     * @param onTextDelta    流式文本增量回调；非流式模式下为空回调
     * @param streaming      true‑启用流式模式；false‑同步非流式
     * @return 标准化Agent执行结果
     * @throws IllegalStateException        工具会话工厂缺失时抛出
     * @throws AgentExecutionCanceledException 检测到任务取消时抛出
     */
    private AgentRuntimeResult executeInternal(AgentRuntimeContext context, BooleanSupplier cancelRequested,
                                                Consumer<String> onTextDelta, boolean streaming) {
        // 执行前先校验是否已经被取消
        requireNotCanceled(cancelRequested);
        // MCP任务依赖模型发起工具调用，能力不满足时在建立MCP连接前直接失败
        ModelAdapter adapter = adapterRegistry.require(context.model(), ModelCapabilities.requiredToolCalling());
        // 任务级MCP资源封装负责鉴权、初始化、取消检测和连接释放
        ExecutionToolSessionFactory toolSessionFactory = toolSessionFactoryProvider.getIfAvailable();
        if (toolSessionFactory == null) {
            throw new IllegalStateException("Skill 工具会话工厂未配置");
        }

        // try‑with‑resources：保证无论正常返回还是异常抛出，工具会话资源一定关闭释放MCP连接
        try (ExecutionToolSession tools = toolSessionFactory.open(context, cancelRequested)) {
            Integer maxOutputTokens = modelMaxOutputTokens(context.model());
            ModelSamplingOptions samplingOptions = ModelSamplingOptions.from(context.model());

            // 构建模型适配器上下文：解密ApiKey、传入全部工具回调集合
            ModelAdapterContext adapterContext = new ModelAdapterContext(
                    context.agent(),
                    context.model(),
                    cryptoService.decrypt(context.model().getEncryptedApiKey()),
                    maxOutputTokens,
                    samplingOptions.temperature(),
                    samplingOptions.topP(),
                    tools.callbacks()).withExecutionId(context.executionId());

            // Token预算优先级：taskInput任务入参 > Agent配置
            Long tokenBudget = context.taskInput().tokenBudget() == null
                    ? context.agent().getTokenBudget() : context.taskInput().tokenBudget();

            // 最大工具迭代轮次优先级：Agent配置 > 全局默认常量
            int maxIterations = context.agent().getMaxIterations() == null
                    ? AgentConstant.DEFAULT_MAX_ITERATIONS : context.agent().getMaxIterations();

            if (streaming) {
                // 流式
                return toolLoop.execute(adapter, adapterContext,
                        context.systemPrompt(), context.instruction(),
                        tokenBudget, maxIterations, cancelRequested, onTextDelta);
            }
            return toolLoop.execute(adapter, adapterContext,
                    context.systemPrompt(), context.instruction(),
                    tokenBudget, maxIterations, cancelRequested);
        }
    }

    /**
     * 读取模型自身的单轮输出Token上限。
     * <p>任务和Agent预算由 {@link ProviderNeutralToolLoop} 按累计用量统一熔断。</p>
     * @param model 模型配置
     * @return 安全的单轮maxCompletionTokens；null表示不做模型级限制
     */
    private Integer modelMaxOutputTokens(ModelEntity model) {
        Long limit = model.getMaxOutputTokens();
        return limit == null ? null : Math.toIntExact(Math.min(limit, Integer.MAX_VALUE));
    }

    /**
     * 检测取消信号，收到取消直接抛出异常终止Agent流程
     * @param cancelRequested 外部回调布尔供应器
     */
    private void requireNotCanceled(BooleanSupplier cancelRequested) {
        if (cancelRequested.getAsBoolean()) {
            throw new AgentExecutionCanceledException();
        }
    }

}
