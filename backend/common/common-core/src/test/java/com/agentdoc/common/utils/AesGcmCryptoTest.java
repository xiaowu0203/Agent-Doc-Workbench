package com.agentdoc.common.utils;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmCryptoTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsAndDecryptsUsingVersionedPayloadFormat() {
        AesGcmCrypto crypto = new AesGcmCrypto(KEY);
        String plaintext = "{\"token\":\"secret\"}";

        String ciphertext = crypto.encrypt(plaintext);

        assertTrue(ciphertext.startsWith("v1:"));
        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, crypto.decrypt(ciphertext));
    }

    @Test
    void generatesDifferentCiphertextForSamePlaintext() {
        AesGcmCrypto crypto = new AesGcmCrypto(KEY);
        String plaintext = "same plaintext";

        assertNotEquals(crypto.encrypt(plaintext), crypto.encrypt(plaintext));
    }

    @Test
    void returnsNullForBlankInput() {
        AesGcmCrypto crypto = new AesGcmCrypto(KEY);

        assertNull(crypto.encrypt(null));
        assertNull(crypto.encrypt("  "));
        assertNull(crypto.decrypt(null));
        assertNull(crypto.decrypt("  "));
    }

    @Test
    void rejectsUnsupportedVersionAndTamperedCiphertext() {
        AesGcmCrypto crypto = new AesGcmCrypto(KEY);
        String ciphertext = crypto.encrypt("plaintext");
        int mutationIndex = ciphertext.length() - 3;
        char replacement = ciphertext.charAt(mutationIndex) == 'A' ? 'B' : 'A';
        String tamperedCiphertext = ciphertext.substring(0, mutationIndex) + replacement
                + ciphertext.substring(mutationIndex + 1);

        assertThrows(IllegalArgumentException.class, () -> crypto.decrypt("v2:payload"));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(tamperedCiphertext));
    }
}
