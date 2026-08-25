package com.agentdoc.agent.execution.model;

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
 * 基于 Spring AI ChatModel 的适配器公共逻辑：发起单轮请求、暴露工具调用结果、统一 Token 结果。
 */
public abstract class AbstractSpringAiModelAdapter implements ModelAdapter {

    private final ModelChatModelCache chatModelCache;

    protected AbstractSpringAiModelAdapter(ModelChatModelCache chatModelCache) {
        this.chatModelCache = chatModelCache;
    }

    @Override
    public ModelTurnResult callOnce(ModelAdapterContext context, List<Message> messages) {
        try {
            ChatModel chatModel = chatModelCache.getOrCreate(context.model().getId(),
                    context.model().getConfigVersion(), () -> chatModel(context));
            return callChatModel(chatModel, messages, context);
        } catch (RuntimeException exception) {
            throw translateException(context, exception);
        }
    }

    @Override
    public ModelTurnResult stream(ModelAdapterContext context, List<Message> messages,
                                  Consumer<String> onTextDelta) {
        try {
            ChatModel chatModel = chatModelCache.getOrCreate(context.model().getId(),
                    context.model().getConfigVersion(), () -> chatModel(context));
            return streamChatModel(chatModel, messages, context,
                    onTextDelta == null ? ignored -> { } : onTextDelta);
        } catch (RuntimeException exception) {
            throw translateException(context, exception);
        }
    }

    protected abstract ChatModel chatModel(ModelAdapterContext context);

    @Override
    public void testConnect(ModelAdapterContext context) {
        ModelAdapterContext testContext = context.forConnectivityTest();
        ChatModel chatModel = null;
        try {
            chatModel = chatModel(testContext);
            callChatModel(chatModel, List.of(new UserMessage("ping")), testContext);
        } catch (RuntimeException exception) {
            throw translateException(testContext, exception);
        } finally {
            if (chatModel != null) {
                closeChatModel(chatModel);
            }
        }
    }

    protected ModelTurnResult callChatModel(ChatModel chatModel, List<Message> messages,
                                            ModelAdapterContext context) {
        ChatOptions options = requestOptions(context);
        Prompt prompt = options == null ? new Prompt(messages) : new Prompt(messages, options);
        ChatResponse response = chatModel.call(prompt);
        return result(response);
    }

    protected ModelTurnResult streamChatModel(ChatModel chatModel, List<Message> messages,
                                              ModelAdapterContext context,
                                              Consumer<String> onTextDelta) {
        ChatOptions options = requestOptions(context);
        Prompt prompt = options == null ? new Prompt(messages) : new Prompt(messages, options);
        StreamAccumulator accumulator = new StreamAccumulator(onTextDelta);
        Flux<ChatResponse> responses = chatModel.stream(prompt);
        responses.doOnNext(accumulator::accept).blockLast();
        return result(accumulator.response());
    }

    private static final class StreamAccumulator {

        private final Consumer<String> onTextDelta;
        private final StringBuilder text = new StringBuilder();
        private final Map<String, AssistantMessage.ToolCall> toolCalls = new LinkedHashMap<>();
        private ChatResponseMetadata responseMetadata;
        private ChatGenerationMetadata generationMetadata;
        private boolean received;

        private StreamAccumulator(Consumer<String> onTextDelta) {
            this.onTextDelta = onTextDelta;
        }

        private void accept(ChatResponse response) {
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return;
            }
            received = true;
            if (response.getMetadata() != null) {
                responseMetadata = response.getMetadata();
            }
            Generation generation = response.getResult();
            generationMetadata = generation.getMetadata();
            AssistantMessage output = generation.getOutput();
            String delta = output.getText();
            if (delta != null && !delta.isEmpty()) {
                text.append(delta);
                onTextDelta.accept(delta);
            }
            for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                AssistantMessage.ToolCall previous = toolCalls.get(toolCall.id());
                toolCalls.put(toolCall.id(), previous == null ? toolCall : merge(previous, toolCall));
            }
        }

        private AssistantMessage.ToolCall merge(AssistantMessage.ToolCall previous,
                                                AssistantMessage.ToolCall current) {
            return new AssistantMessage.ToolCall(previous.id(),
                    current.type() == null ? previous.type() : current.type(),
                    current.name() == null ? previous.name() : current.name(),
                    safe(previous.arguments()) + safe(current.arguments()));
        }

        private String safe(String value) {
            return value == null ? "" : value;
        }

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
     * 返回本次请求的动态选项。工具回调属于任务级状态，不能放入缓存 ChatModel 的默认选项。
     */
    protected ChatOptions requestOptions(ModelAdapterContext context) {
        return null;
    }

    /**
     * 释放适配器创建的 ChatModel 资源；仅用于连通性测试和其他非缓存实例。
     * <p>缓存实例由 ModelChatModelCache 在淘汰、失效或应用关闭时释放。</p>
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

    protected TokenValue cachedInputTokens(Usage usage) {
        return TokenValue.unavailable();
    }

    /**
     * 将 Spring AI、HTTP 客户端和厂商 SDK 异常翻译为统一模型异常。
     * <p>保留原始异常作为 cause，便于日志和问题排查；调用方只依赖翻译后的字段。</p>
     */
    protected ModelProviderException translateException(ModelAdapterContext context, RuntimeException exception) {
        if (exception instanceof ModelProviderException modelProviderException) {
            return modelProviderException;
        }
        Throwable root = NestedExceptionUtils.getMostSpecificCause(exception);
        Integer statusCode = statusCode(exception, root);
        String message = safeMessage(root);
        ModelErrorType errorType = classify(exception, root, statusCode, message);
        boolean retryable = retryable(exception, root, statusCode, errorType);
        ModelEntity model = context == null ? null : context.model();
        String provider = model == null ? null : model.getProvider();
        return new ModelProviderException(provider, errorType, statusCode,
                providerCode(root), retryable, "模型调用失败: " + message, exception);
    }

    protected ModelProviderException translateException(RuntimeException exception) {
        return translateException(null, exception);
    }

    private ModelErrorType classify(Throwable exception, Throwable root, Integer statusCode, String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
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
        if (exception instanceof TransientAiException || root instanceof TransientAiException) {
            return ModelErrorType.PROVIDER_UNAVAILABLE;
        }
        if (containsAny(normalized, "api key", "authentication", "unauthorized", "forbidden",
                "invalid token", "permission denied")) {
            return ModelErrorType.AUTHENTICATION;
        }
        if (containsAny(normalized, "rate limit", "too many requests", "throttl", "quota exceeded")) {
            return ModelErrorType.RATE_LIMIT;
        }
        if (containsAny(normalized, "timed out", "timeout", "deadline exceeded")) {
            return ModelErrorType.TIMEOUT;
        }
        if (root instanceof TimeoutException || root instanceof SocketTimeoutException) {
            return ModelErrorType.TIMEOUT;
        }
        if (root instanceof ConnectException || root instanceof UnknownHostException
                || root instanceof IOException || exception instanceof ResourceAccessException
                || exception instanceof WebClientRequestException) {
            return ModelErrorType.PROVIDER_UNAVAILABLE;
        }
        return ModelErrorType.PROVIDER_ERROR;
    }

    private boolean retryable(Throwable exception, Throwable root, Integer statusCode, ModelErrorType errorType) {
        if (exception instanceof NonTransientAiException || root instanceof NonTransientAiException) {
            return false;
        }
        if (exception instanceof TransientAiException || root instanceof TransientAiException) {
            return true;
        }
        if (statusCode != null) {
            return statusCode == 408 || statusCode == 409 || statusCode == 425
                    || statusCode == 429 || statusCode >= 500;
        }
        return errorType == ModelErrorType.TIMEOUT || errorType == ModelErrorType.PROVIDER_UNAVAILABLE;
    }

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

    private String providerCode(Throwable root) {
        return root.getClass().getSimpleName();
    }

    private String safeMessage(Throwable exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
