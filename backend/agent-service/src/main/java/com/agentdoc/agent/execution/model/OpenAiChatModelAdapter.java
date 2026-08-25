package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.pojo.TokenValue;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * OpenAI Chat Completions 适配器，同时覆盖 OpenAI-compatible 供应商。
 */
@Component
public class OpenAiChatModelAdapter extends AbstractSpringAiModelAdapter {

    public OpenAiChatModelAdapter(ModelChatModelCache chatModelCache) {
        super(chatModelCache);
    }

    @Override
    public Set<ModelAdapterType> supportedTypes() {
        return Set.of(ModelAdapterType.OPENAI_CHAT, ModelAdapterType.OPENAI_COMPATIBLE);
    }

    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(true, true);
    }

    @Override
    protected ChatModel chatModel(ModelAdapterContext context) {
        ModelEntity model = context.model();
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(ModelEndpoint.resolve(model))
                .apiKey(context.apiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model.getModelKey())
                .internalToolExecutionEnabled(false)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    @Override
    protected ChatOptions requestOptions(ModelAdapterContext context) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .model(context.model().getModelKey())
                .toolCallbacks(context.toolCallbacks())
                .internalToolExecutionEnabled(false);
        if (context.maxOutputTokens() != null) {
            options.maxCompletionTokens(context.maxOutputTokens());
        }
        return options.build();
    }

    @Override
    protected TokenValue cachedInputTokens(Usage usage) {
        if (usage == null || !(usage.getNativeUsage() instanceof OpenAiApi.Usage nativeUsage)
                || nativeUsage.promptTokensDetails() == null
                || nativeUsage.promptTokensDetails().cachedTokens() == null) {
            return TokenValue.unavailable();
        }
        return TokenValue.provider(nativeUsage.promptTokensDetails().cachedTokens().longValue());
    }
}
