package com.agentdoc.common.utils;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class ReportProofSignatureTest {
    @Test
    void bindsRequestAndRejectsExpiredOrInvalidProof() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        String hash = "a".repeat(64);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String token = ReportProofSignature.sign(key, 1L, 2L, hash, now);
        assertTrue(ReportProofSignature.verify(key, 1L, 2L, hash, token, now.plusSeconds(60)));
        assertFalse(ReportProofSignature.verify(key, 1L, 2L, hash, token, now.plusSeconds(61)));
        assertFalse(ReportProofSignature.verify(key, 1L, 3L, hash, token, now));
        assertFalse(ReportProofSignature.verify(key, 1L, 2L, "b".repeat(64), token, now));
        assertFalse(ReportProofSignature.verify("", 1L, 2L, hash, token, now));
        assertFalse(ReportProofSignature.verify(key, 1L, 2L, hash, null, now));
        assertFalse(ReportProofSignature.verify(key, 1L, 2L, hash, token, now.minusSeconds(1)));
    }
}
