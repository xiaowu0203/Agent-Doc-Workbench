package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.pojo.TokenValue;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Claude Messages API 适配器。 */
@Component
public class AnthropicModelAdapter extends AbstractSpringAiModelAdapter {

    public AnthropicModelAdapter(ModelChatModelCache chatModelCache) {
        super(chatModelCache);
    }

    @Override
    public Set<ModelAdapterType> supportedTypes() {
        return Set.of(ModelAdapterType.ANTHROPIC_MESSAGES);
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(true, true);
    }

    @Override
    protected ChatModel chatModel(ModelAdapterContext context) {
        ModelEntity model = context.model();
        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(ModelEndpoint.resolve(model))
                .apiKey(context.apiKey())
                .build();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(model.getModelKey())
                .internalToolExecutionEnabled(false)
                .build();
        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .build();
    }

    @Override
    protected ChatOptions requestOptions(ModelAdapterContext context) {
        AnthropicChatOptions.Builder options = AnthropicChatOptions.builder()
                .model(context.model().getModelKey())
                .toolCallbacks(context.toolCallbacks())
                .internalToolExecutionEnabled(false);
        if (context.maxOutputTokens() != null) {
            options.maxTokens(context.maxOutputTokens());
        }
        return options.build();
    }

    @Override
    protected TokenValue cachedInputTokens(Usage usage) {
        if (usage == null || !(usage.getNativeUsage() instanceof AnthropicApi.Usage nativeUsage)) {
            return TokenValue.unavailable();
        }
        return TokenValue.provider(nativeUsage.cacheReadInputTokens() == null
                ? null : nativeUsage.cacheReadInputTokens().longValue());
    }
}
