package com.agentdoc.task.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class McpConfigCryptoServiceTest {

    @Test
    void encryptsAndDecryptsWithoutStoringPlaintext() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        McpConfigCryptoService service = new McpConfigCryptoService(key);

        String plaintext = "{\"endpoint\":\"https://agent.example/mcp\",\"token\":\"secret\"}";
        String ciphertext = service.encrypt(plaintext);

        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, service.decrypt(ciphertext));
    }
}
