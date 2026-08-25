package com.agentdoc.agent.execution.model;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import com.agentdoc.agent.enums.ModelErrorType;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.pojo.TokenValue;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Flux;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;

/**
 * 基于Spring‑AI ChatModel实现的ModelAdapter抽象公共实现
 * <p>
 * 封装通用逻辑：
 * <ul>
 *     <li>ChatModel实例缓存管理，复用{@link ModelChatModelCache}</li>
 *     <li>同步模型调用 callOnce</li>
 *     <li>流式模型调用 stream，内置分片累加器{@link StreamAccumulator}</li>
 *     <li>模型返回结果解析、Token用量提取封装{@link #result(ChatResponse)}</li>
 *     <li>统一异常翻译：将SDK、HTTP异常转为项目标准{@link ModelProviderException}</li>
 *     <li>模型连通性测试 testConnect</li>
 *     <li>非缓存ChatModel资源释放逻辑 closeChatModel</li>
 * </ul>
 * <p>
 * 子类仅需要实现 {@link #chatModel(ModelAdapterContext context)}，完成对应厂商ChatModel对象构建。
 * <b>重要约束：任务级工具回调、动态prompt参数禁止修改缓存ChatModel默认options，统一通过{@link #requestOptions}在Prompt维度传递</b>
 */
public abstract class AbstractSpringAiModelAdapter implements ModelAdapter {
    /**
     * ChatModel实例缓存管理器；以modelId+configVersion作为key做实例复用
     */
    private final ModelChatModelCache chatModelCache;

    protected AbstractSpringAiModelAdapter(ModelChatModelCache chatModelCache) {
        this.chatModelCache = chatModelCache;
    }

    /**
     * 同步单次模型调用；上层不做循环，仅执行一轮LLM请求
     * @param context 模型适配器上下文，携带模型配置、解密后密钥、工具回调集合
     * @param messages 完整对话消息列表
     * @return 模型单轮调用结果，包含原始response、文本、token用量
     */
    @Override
    public ModelTurnResult callOnce(ModelAdapterContext context, List<Message> messages) {
        try {
            ChatModel chatModel = cachedChatModel(context);
            return callChatModel(chatModel, messages, context);
        } catch (RuntimeException exception) {
            // 捕获底层运行时异常，翻译为统一模型异常后抛出
            throw translateException(context, exception);
        }
    }

    /**
     * 获取缓存的ChatModel实例；命中缓存直接返回，未命中调用子类chatModel()创建并存入缓存
     * @param context 模型适配器上下文
     * @return 可复用ChatModel实例（来自缓存）
     */
    @Override
    public ChatModel cachedChatModel(ModelAdapterContext context) {
        return chatModelCache.getOrCreate(context.model().getId(),
                context.model().getConfigVersion(), () -> chatModel(context));
    }

    /**
     * 获取本次请求动态ChatOptions；由子类实现返回任务级动态参数
     * <p>禁止修改缓存ChatModel内部默认Options，全部通过Prompt携带动态参数</p>
     * @param context 模型适配器上下文
     * @return ChatOptions，可为null
     */
    @Override
    public ChatOptions chatOptions(ModelAdapterContext context) {
        return requestOptions(context);
    }

    /**
     * 异常翻译SPI：将底层SDK/HTTP异常转换为项目统一模型异常
     * @param context 模型适配器上下文
     * @param exception 原始运行时异常
     * @return 翻译完成的模型异常
     */
    @Override
    public RuntimeException translateForRuntime(ModelAdapterContext context, RuntimeException exception) {
        return translateException(context, exception);
    }

    /**
     * 从ChatResponse提取TokenUsage；对外提供工具，上层Runtime可直接复用解析逻辑
     * @param response 模型原始响应对象
     * @return 解析后的Token用量对象
     */
    @Override
    public TokenUsage tokenUsage(ChatResponse response) {
        return result(response).tokenUsage();
    }

    /**
     * 流式模型调用；内部通过StreamAccumulator聚合分片，回调文本增量onTextDelta
     * @param context 模型适配器上下文
     * @param messages 对话消息列表
     * @param onTextDelta 文本增量回调，接收每一段返回文本delta
     * @return 完整模型轮次结果（流全部消费完成后返回聚合完成对象）
     */
    @Override
    public ModelTurnResult stream(ModelAdapterContext context, List<Message> messages,
                                  Consumer<String> onTextDelta) {
        try {
            ChatModel chatModel = cachedChatModel(context);
            return streamChatModel(chatModel, messages, context,
                    onTextDelta == null ? ignored -> { } : onTextDelta);
        } catch (RuntimeException exception) {
            throw translateException(context, exception);
        }
    }

    /**
     * 子类扩展点：根据上下文构建厂商原生ChatModel实例；该实例会被缓存管理器缓存复用
     * @param context 模型适配器上下文
     * @return 厂商实现的ChatModel对象
     */
    protected abstract ChatModel chatModel(ModelAdapterContext context);

    /**
     * 模型连通性测试；用于校验API‑Key、baseUrl等配置是否合法可用
     * <p>注意：测试使用独立非缓存ChatModel实例，执行完成后会执行资源释放</p>
     * @param context 模型适配器上下文
     */
    @Override
    public void testConnect(ModelAdapterContext context) {
        // 构建连通性测试专用上下文
        ModelAdapterContext testContext = context.forConnectivityTest();
        ChatModel chatModel = null;
        try {
            // 创建全新ChatModel，不走缓存，避免污染缓存池
            chatModel = chatModel(testContext);
            // 发送ping探测请求，不携带工具，仅验证连通
            callChatModel(chatModel, List.of(new UserMessage("ping")), testContext);
        } catch (RuntimeException exception) {
            throw translateException(testContext, exception);
        } finally {
            // 释放测试用非缓存ChatModel资源
            if (chatModel != null) {
                closeChatModel(chatModel);
            }
        }
    }

    /**
     * 执行同步模型调用；组装Prompt、调用chatModel.call，包装返回ModelTurnResult
     * @param chatModel 模型实例
     * @param messages 消息列表
     * @param context 适配器上下文
     * @return 解析后模型轮次结果
     */
    protected ModelTurnResult callChatModel(ChatModel chatModel, List<Message> messages,
                                            ModelAdapterContext context) {
        ChatOptions options = requestOptions(context);
        Prompt prompt = options == null ? new Prompt(messages) : new Prompt(messages, options);
        ChatResponse response = chatModel.call(prompt);
        return result(response);
    }

    /**
     * 执行流式模型调用；消费Flux流，使用StreamAccumulator聚合分片、回调增量文本，blockLast等待流全部结束
     * @param chatModel 模型实例
     * @param messages 消息列表
     * @param context 适配器上下文
     * @param onTextDelta 文本增量回调
     * @return 聚合完成后的完整模型轮次结果
     */
    protected ModelTurnResult streamChatModel(ChatModel chatModel, List<Message> messages,
                                              ModelAdapterContext context,
                                              Consumer<String> onTextDelta) {
        ChatOptions options = requestOptions(context);
        Prompt prompt = options == null ? new Prompt(messages) : new Prompt(messages, options);
        StreamAccumulator accumulator = new StreamAccumulator(onTextDelta);
        Flux<ChatResponse> responses = chatModel.stream(prompt);
        // 消费全部流分片，每一片交给累加器；阻塞直到流结束
        responses.doOnNext(accumulator::accept).blockLast();
        return result(accumulator.response());
    }

    /**
     * 流式分片累加器：聚合多个流式ChatResponse分片，合并文本、合并toolCall参数，对外推送文本增量delta
     * <p>Spring‑AI流式会把回答、toolCall拆分为多个分片返回；本类负责合并为完整AssistantMessage</p>
     */
    private static final class StreamAccumulator {
        /** 文本增量回调，向外输出每一段delta文本 */
        private final Consumer<String> onTextDelta;
        /** 拼接完整回答文本 */
        private final StringBuilder text = new StringBuilder();
        /** toolCall按id合并，key=toolCallId */
        private final Map<String, AssistantMessage.ToolCall> toolCalls = new LinkedHashMap<>();
        /** 模型响应元数据 */
        private ChatResponseMetadata responseMetadata;
        /** 单条generation元数据 */
        private ChatGenerationMetadata generationMetadata;
        /** 是否收到过至少一个有效分片 */
        private boolean received;

        private StreamAccumulator(Consumer<String> onTextDelta) {
            this.onTextDelta = onTextDelta;
        }

        /**
         * 处理每一个流式分片ChatResponse
         * @param response 流分片对象
         */
        private void accept(ChatResponse response) {
            // 若响应为空直接返回
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return;
            }
            // 标记【是否收到过至少一个有效分片】
            received = true;
            if (response.getMetadata() != null) {
                responseMetadata = response.getMetadata();
            }
            // 获取响应结果
            Generation generation = response.getResult();
            // 获取元数据
            generationMetadata = generation.getMetadata();
            // 获取输出
            AssistantMessage output = generation.getOutput();
            // 获取输出（文本）
            String delta = output.getText();
            if (StringUtils.isNotBlank(delta)) {
                // 不为空则进行追加
                text.append(delta);
                // 文本增量回调，向外输出每一段delta文本
                onTextDelta.accept(delta);
            }
            // 合并toolCall分片，同一个id的toolCall做增量merge
            for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                AssistantMessage.ToolCall previous = toolCalls.get(toolCall.id());
                toolCalls.put(toolCall.id(), previous == null ? toolCall : merge(previous, toolCall));
            }
        }

        /**
         * 合并同一个toolCall的新旧分片；type/name取非null新值；arguments字符串做拼接
         * @param previous 上一分片toolCall
         * @param current 当前分片toolCall
         * @return 合并完成的完整ToolCall
         */
        private AssistantMessage.ToolCall merge(AssistantMessage.ToolCall previous,
                                                AssistantMessage.ToolCall current) {
            return new AssistantMessage.ToolCall(previous.id(),
                    current.type() == null ? previous.type() : current.type(),
                    current.name() == null ? previous.name() : current.name(),
                    safe(previous.arguments()) + safe(current.arguments()));
        }

        /** null安全获取字符串，null返回空字符串 */
        private String safe(String value) {
            return value == null ? "" : value;
        }

        /**
         * 流全部接收完毕，组装完整ChatResponse
         * @return 聚合完成的完整ChatResponse
         * @throws IllegalStateException 未收到任何模型分片
         */
        private ChatResponse response() {
            if (!received) {
                throw new IllegalStateException("模型流式调用未返回执行结果");
            }
            AssistantMessage output = AssistantMessage.builder()
                    .content(text.toString())
                    .toolCalls(new ArrayList<>(toolCalls.values()))
                    .build();
            Generation generation = generationMetadata == null
                    ? new Generation(output) : new Generation(output, generationMetadata);
            return responseMetadata == null
                    ? new ChatResponse(List.of(generation))
                    : new ChatResponse(List.of(generation), responseMetadata);
        }
    }

    /**
     * 获取本次请求动态ChatOptions；子类重写提供单次请求动态参数
     * <p><b>重要：任务级工具回调、动态参数严禁设置到缓存ChatModel默认options，必须在这里返回，作用于单次Prompt</b></p>
     * @param context 模型适配器上下文
     * @return ChatOptions，默认返回null
     */
    protected ChatOptions requestOptions(ModelAdapterContext context) {
        return null;
    }

    /**
     * 释放非缓存ChatModel实例资源；仅用于连通性测试等临时创建的实例
     * <p>缓存池内ChatModel生命周期由{@link ModelChatModelCache}负责淘汰/关闭，不要手动调用本方法</p>
     * @param chatModel 待关闭模型实例
     */
    protected void closeChatModel(ChatModel chatModel) {
        if (chatModel instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw exception instanceof RuntimeException runtimeException
                        ? runtimeException : new IllegalStateException("模型客户端释放失败", exception);
            }
        }
    }

    /**
     * 将原始ChatResponse转换为上层统一的{@link ModelTurnResult}，解析token用量
     * @param response 模型原生响应
     * @return 封装后的模型轮次结果
     */
    protected ModelTurnResult result(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型未返回执行结果");
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        Long inputTokens = usage == null || usage.getPromptTokens() == null
                ? null : usage.getPromptTokens().longValue();
        Long outputTokens = usage == null || usage.getCompletionTokens() == null
                ? null : usage.getCompletionTokens().longValue();
        return new ModelTurnResult(response, response.getResult().getOutput().getText(),
                new TokenUsage(TokenValue.provider(inputTokens), cachedInputTokens(usage),
                        TokenValue.provider(outputTokens)));
    }

    /**
     * 解析cached‑input tokens（缓存prompt tokens，部分厂商支持）
     * @param usage 模型usage元数据
     * @return TokenValue，默认返回unavailable，子类可重写实现厂商特有缓存token解析
     */
    protected TokenValue cachedInputTokens(Usage usage) {
        return TokenValue.unavailable();
    }

    /**
     * 通用异常翻译：把Spring‑AI、HTTP、SDK原始异常统一转为{@link ModelProviderException}
     * <p>保留原始异常cause，便于日志排查；上层业务只依赖翻译后的错误类型、statusCode、retryable字段</p>
     * @param context 模型适配器上下文
     * @param exception 原始运行时异常
     * @return 翻译完成的模型提供方异常
     */
    protected ModelProviderException translateException(ModelAdapterContext context, RuntimeException exception) {
        // 已经是标准模型异常直接返回，不需要二次转换
        if (exception instanceof ModelProviderException modelProviderException) {
            return modelProviderException;
        }
        // 获取最内层根异常
        Throwable root = NestedExceptionUtils.getMostSpecificCause(exception);
        Integer statusCode = statusCode(exception, root);
        String message = safeMessage(root);
        // 根据异常信息、状态码归类错误类型
        ModelErrorType errorType = classify(exception, root, statusCode, message);
        // 判断该错误是否支持重试
        boolean retryable = retryable(exception, root, statusCode, errorType);

        ModelEntity model = context == null ? null : context.model();
        String provider = model == null ? null : model.getProvider();
        return new ModelProviderException(provider, errorType, statusCode,
                providerCode(root), retryable, "模型调用失败: " + message, exception);
    }

    /**
     * 无上下文版本异常翻译
     * @param exception 原始异常
     * @return 翻译后模型异常
     */
    protected ModelProviderException translateException(RuntimeException exception) {
        return translateException(null, exception);
    }

    /**
     * 根据异常栈、http状态码、错误消息，分类得到{@link ModelErrorType}
     * @param exception 外层异常
     * @param root 根cause异常
     * @param statusCode http状态码，可为null
     * @param message 异常消息
     * @return 归类后的错误枚举
     */
    private ModelErrorType classify(Throwable exception, Throwable root, Integer statusCode, String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        // HTTP状态码优先判断
        if (statusCode != null) {
            if (statusCode == 401 || statusCode == 403) {
                return ModelErrorType.AUTHENTICATION;
            }
            if (statusCode == 429) {
                return ModelErrorType.RATE_LIMIT;
            }
            if (statusCode == 413 || containsAny(normalized, "context length", "maximum context",
                    "prompt too long", "too many tokens", "token limit")) {
                return ModelErrorType.CONTEXT_LENGTH;
            }
            if (statusCode == 408 || statusCode == 504) {
                return ModelErrorType.TIMEOUT;
            }
            if (statusCode >= 500) {
                return ModelErrorType.PROVIDER_UNAVAILABLE;
            }
            if (statusCode >= 400) {
                return ModelErrorType.INVALID_REQUEST;
            }
        }
        // 项目自定义瞬时异常
        if (exception instanceof TransientAiException || root instanceof TransientAiException) {
            return ModelErrorType.PROVIDER_UNAVAILABLE;
        }
        // 文本关键字匹配鉴权类错误
        if (containsAny(normalized, "api key", "authentication", "unauthorized", "forbidden",
                "invalid token", "permission denied")) {
            return ModelErrorType.AUTHENTICATION;
        }
        // 文本关键字匹配鉴权类错误
        if (containsAny(normalized, "rate limit", "too many requests", "throttl", "quota exceeded")) {
            return ModelErrorType.RATE_LIMIT;
        }
        // 限流关键字
        if (containsAny(normalized, "timed out", "timeout", "deadline exceeded")) {
            return ModelErrorType.TIMEOUT;
        }
        // JDK底层超时异常类
        if (root instanceof TimeoutException || root instanceof SocketTimeoutException) {
            return ModelErrorType.TIMEOUT;
        }
        // 网络IO、连接异常，判定为服务不可用
        if (root instanceof ConnectException || root instanceof UnknownHostException
                || root instanceof IOException || exception instanceof ResourceAccessException
                || exception instanceof WebClientRequestException) {
            return ModelErrorType.PROVIDER_UNAVAILABLE;
        }
        // 默认归类为厂商内部未知错误
        return ModelErrorType.PROVIDER_ERROR;
    }

    /**
     * 判断当前异常是否可重试
     * @param exception 外层异常
     * @param root 根cause
     * @param statusCode http状态码
     * @param errorType 已归类错误类型
     * @return true=可重试；false=不可重试
     */
    private boolean retryable(Throwable exception, Throwable root, Integer statusCode, ModelErrorType errorType) {
        // 明确标记为非瞬时异常，禁止重试
        if (exception instanceof NonTransientAiException || root instanceof NonTransientAiException) {
            return false;
        }
        // 标记瞬时异常，允许重试
        if (exception instanceof TransientAiException || root instanceof TransientAiException) {
            return true;
        }
        // HTTP状态码层面可重试状态码
        if (statusCode != null) {
            return statusCode == 408 || statusCode == 409 || statusCode == 425
                    || statusCode == 429 || statusCode >= 500;
        }
        // 超时、服务不可用类型允许重试
        return errorType == ModelErrorType.TIMEOUT || errorType == ModelErrorType.PROVIDER_UNAVAILABLE;
    }

    /**
     * 从异常栈提取HTTP响应状态码，兼容WebClient、RestTemplate两类http异常
     * @param exception 外层异常
     * @param root 根cause
     * @return http状态码；无则返回null
     */
    private Integer statusCode(Throwable exception, Throwable root) {
        if (exception instanceof WebClientResponseException webException) {
            return webException.getStatusCode().value();
        }
        if (exception instanceof HttpStatusCodeException httpException) {
            return httpException.getStatusCode().value();
        }
        if (root instanceof WebClientResponseException webException) {
            return webException.getStatusCode().value();
        }
        if (root instanceof HttpStatusCodeException httpException) {
            return httpException.getStatusCode().value();
        }
        return null;
    }

    /**
     * 获取厂商原始异常类名，作为providerCode字段用于日志监控
     * @param root 根异常
     * @return 异常简单类名
     */
    private String providerCode(Throwable root) {
        return root.getClass().getSimpleName();
    }

    /**
     * null安全获取异常message；message为空则返回异常类名
     * @param exception 异常对象
     * @return 非空异常消息
     */
    private String safeMessage(Throwable exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    /**
     * 判断字符串是否包含候选关键字中任意一个
     * @param value 待检测文本（已经转小写）
     * @param candidates 候选关键字数组
     * @return true：命中任意关键字
     */
    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
