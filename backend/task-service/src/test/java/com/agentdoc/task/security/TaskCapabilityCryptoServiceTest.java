package com.agentdoc.task.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TaskCapabilityCryptoServiceTest {

    @Test
    void encryptsAndDecryptsWithoutStoringPlaintext() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        TaskCapabilityCryptoService service = new TaskCapabilityCryptoService(key);

        String plaintext = "{\"endpoint\":\"https://agent.example/mcp\",\"token\":\"secret\"}";
        String ciphertext = service.encrypt(plaintext);

        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, service.decrypt(ciphertext));
    }
}
