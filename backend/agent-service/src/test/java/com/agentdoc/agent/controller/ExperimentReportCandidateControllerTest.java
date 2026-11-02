package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentCandidateConfigService;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.ReportProofSignature;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ExperimentReportCandidateControllerTest {
    @Test
    void verifiesSignatureBeforeCallingFullCandidateProof() {
        AgentCandidateConfigService service = mock(AgentCandidateConfigService.class);
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        String hash = "a".repeat(64);
        ExperimentReportCandidateController controller = new ExperimentReportCandidateController(service, key);
        assertThrows(BusinessException.class, () -> controller.identity(1L, 2L, hash, null));
        assertThrows(BusinessException.class, () -> controller.identity(1L, 2L, hash,
                ReportProofSignature.sign(key, 1L, 2L, hash, Instant.now().minusSeconds(120))));
        assertThrows(BusinessException.class, () -> controller.identity(1L, 3L, hash,
                ReportProofSignature.sign(key, 1L, 2L, hash, Instant.now())));
        assertThrows(BusinessException.class, () -> new ExperimentReportCandidateController(service, "")
                .identity(1L, 2L, hash, ReportProofSignature.sign(key, 1L, 2L, hash, Instant.now())));
        verifyNoInteractions(service);
        controller.identity(1L, 2L, hash, ReportProofSignature.sign(key, 1L, 2L, hash, Instant.now()));
        verify(service).requireRestorableIdentity(1L, 2L, hash);
    }
}
