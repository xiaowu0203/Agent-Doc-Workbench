package com.agentdoc.agent.convertor;

import com.agentdoc.agent.pojo.dto.ModelUpdateDTO;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ModelConvertorUpdateTest {

    @Test
    void preservesSecretAndIncrementsVersionWhenApiKeyIsBlank() {
        ModelEntity entity = new ModelEntity();
        entity.setEncryptedApiKey("encrypted-old-key");
        entity.setConfigVersion(3L);
        ModelUpdateDTO dto = new ModelUpdateDTO("deepseek", "anthropic-messages", "deepseek-chat", "DeepSeek V3",
                null, "https://api.deepseek.com", null, null, 128000L, 8192L,
                new BigDecimal("2"), new BigDecimal("6"), "推理模型");

        ModelConvertor.applyUpdate(entity, dto, null);

        assertThat(entity.getEncryptedApiKey()).isEqualTo("encrypted-old-key");
        assertThat(entity.getAdapterType()).isEqualTo("openai-compatible");
        assertThat(entity.getConfigVersion()).isEqualTo(4L);
        assertThat(entity.getDisplayName()).isEqualTo("DeepSeek V3");
    }
}
