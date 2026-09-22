package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.config.EvaluationProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerCapabilityCryptoServiceTest {

    private static final String CURRENT_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String PREVIOUS_KEY = Base64.getEncoder()
            .encodeToString("abcdef0123456789".getBytes(StandardCharsets.UTF_8));

    @Test
    void encryptsWithCurrentKeyAndReadsPreviousVersionDuringRotation() {
        WorkerCapabilityCryptoService previous = new WorkerCapabilityCryptoService(
                new EvaluationProperties("v1", PREVIOUS_KEY, null, null));
        var oldCiphertext = previous.encrypt("old-token");
        WorkerCapabilityCryptoService current = new WorkerCapabilityCryptoService(
                new EvaluationProperties("v2", CURRENT_KEY, "v1", PREVIOUS_KEY));

        var newCiphertext = current.encrypt("new-token");

        assertThat(newCiphertext.keyVersion()).isEqualTo("v2");
        assertThat(current.decrypt(newCiphertext.keyVersion(), newCiphertext.ciphertext())).isEqualTo("new-token");
        assertThat(current.decrypt(oldCiphertext.keyVersion(), oldCiphertext.ciphertext())).isEqualTo("old-token");
        assertThat(newCiphertext.ciphertext()).doesNotContain("new-token");
    }
}
