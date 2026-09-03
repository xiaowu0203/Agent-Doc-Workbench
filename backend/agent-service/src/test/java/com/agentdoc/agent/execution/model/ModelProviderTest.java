package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.convertor.ModelConvertor;
import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.enums.ModelProvider;
import com.agentdoc.agent.pojo.dto.ModelCreateDTO;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelProviderTest {

    @Test
    void providerDefaultsToExpectedAdapter() {
        assertEquals(ModelAdapterType.OPENAI_CHAT, ModelProvider.fromCode("openai").defaultAdapterType());
        assertEquals(ModelAdapterType.ANTHROPIC_MESSAGES, ModelProvider.fromCode("claude").defaultAdapterType());
        assertEquals(ModelAdapterType.GOOGLE_GENAI, ModelProvider.fromCode("gemini").defaultAdapterType());
        assertEquals(ModelAdapterType.OPENAI_COMPATIBLE, ModelProvider.fromCode("qwen").defaultAdapterType());
    }

    @Test
    void modelCreationUsesProviderAdapterEvenWhenAnotherAdapterIsSubmitted() {
        ModelCreateDTO dto = new ModelCreateDTO("deepseek", "openai-compatible", "deepseek-v4-flash", "DeepSeek",
                null, "https://api.deepseek.com/anthropic", "secret", null,
                null, null, null, null, null);

        ModelEntity entity = ModelConvertor.toEntity(dto, "encrypted");

        assertEquals("deepseek", entity.getProvider());
        assertEquals(ModelAdapterType.ANTHROPIC_MESSAGES.getCode(), entity.getAdapterType());
        assertEquals("encrypted", entity.getEncryptedApiKey());
    }
}
