package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.pojo.entity.ModelEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSecretRenderingTest {

    @Test
    void adapterContextRedactsApiKeyInToString() {
        String rendered = new ModelAdapterContext(null, null, "plain-api-key", 100, List.of()).toString();

        assertFalse(rendered.contains("plain-api-key"));
        assertTrue(rendered.contains("<redacted>"));
    }

    @Test
    void modelEntityExcludesEncryptedApiKeyFromToString() {
        ModelEntity model = new ModelEntity();
        model.setEncryptedApiKey("encrypted-api-key");

        assertFalse(model.toString().contains("encrypted-api-key"));
    }
}
