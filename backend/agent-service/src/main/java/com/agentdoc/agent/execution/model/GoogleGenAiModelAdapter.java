package com.agentdoc.agent.execution.model;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.common.pojo.TokenValue;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Set;

/** Gemini Generative AI SDK 适配器。 */
@Component
public class GoogleGenAiModelAdapter extends AbstractSpringAiModelAdapter {

    public GoogleGenAiModelAdapter(ModelChatModelCache chatModelCache) {
        super(chatModelCache);
    }

    @Override
    public Set<ModelAdapterType> supportedTypes() {
        return Set.of(ModelAdapterType.GOOGLE_GENAI);
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(true, true);
    }

    @Override
    protected ChatModel chatModel(ModelAdapterContext context) {
        Client.Builder clientBuilder = Client.builder().apiKey(context.apiKey());
        if (context.model().getBaseUrl() != null && !context.model().getBaseUrl().isBlank()) {
            clientBuilder.httpOptions(HttpOptions.builder().baseUrl(context.model().getBaseUrl()).build());
        }
        Client client = clientBuilder.build();
        try {
            GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                    .model(context.model().getModelKey())
                    .internalToolExecutionEnabled(false)
                    .build();
            ChatModel chatModel = GoogleGenAiChatModel.builder()
                    .genAiClient(client)
                    .defaultOptions(options)
                    .build();
            return new ClientBackedChatModel(chatModel, client);
        } catch (RuntimeException exception) {
            try {
                client.close();
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    @Override
    protected ChatOptions requestOptions(ModelAdapterContext context) {
        GoogleGenAiChatOptions.Builder options = GoogleGenAiChatOptions.builder()
                .model(context.model().getModelKey())
                .toolCallbacks(context.toolCallbacks())
                .internalToolExecutionEnabled(false);
        if (context.maxOutputTokens() != null) {
            options.maxOutputTokens(context.maxOutputTokens());
        }
        return options.build();
    }

    @Override
    protected TokenValue cachedInputTokens(Usage usage) {
        if (usage instanceof GoogleGenAiUsage googleUsage
                && googleUsage.getCachedContentTokenCount() != null) {
            return TokenValue.provider(googleUsage.getCachedContentTokenCount().longValue());
        }
        return TokenValue.unavailable();
    }

    /** 将 Gemini ChatModel 与其专属 Client 绑定，交由 ChatModel 缓存在淘汰时释放。 */
    private static final class ClientBackedChatModel implements ChatModel, AutoCloseable {

        private final ChatModel delegate;
        private final Client client;

        private ClientBackedChatModel(ChatModel delegate, Client client) {
            this.delegate = delegate;
            this.client = client;
        }

/**
 * 重写call方法，用于处理聊天请求
 *
 * @param prompt 包含用户输入提示的Prompt对象
 * @return 返回ChatResponse类型的聊天响应结果
 * @Override 注解表示重写父类的方法
 */
        @Override
        public ChatResponse call(Prompt prompt) {
    // 通过委托模式(delegate)调用call方法，将传入的prompt参数传递给委托对象
            return delegate.call(prompt);
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return delegate.getDefaultOptions();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return delegate.stream(prompt);
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
