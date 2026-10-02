package com.agentdoc.document.service;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.security.TaskRecoveryVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** 恢复草稿窄凭证入口；不修改 SecurityContext/TaskCapabilityContext。 */
@Service
@RequiredArgsConstructor
public class DocumentTaskRecoveryService {
    private final TaskRecoveryVerifier verifier;
    private final DocumentService documentService;

    public void finalizeDraft(Long taskId, String token) {
        Jwt jwt = verifier.verifyDraft(token, taskId);
        documentService.finalizeRecoveredTaskDraft(
                Long.valueOf(jwt.getClaimAsString(JwtConstant.CLAIM_DOCUMENT_ID)),
                Long.valueOf(jwt.getClaimAsString(JwtConstant.CLAIM_SPACE_ID)), taskId,
                Long.valueOf(jwt.getClaimAsString(JwtConstant.CLAIM_AGENT_ID)),
                Long.valueOf(jwt.getClaimAsString(JwtConstant.CLAIM_DOCUMENT_VERSION_SNAPSHOT)),
                jwt.getClaimAsString(JwtConstant.CLAIM_DOCUMENT_CONTENT_SHA256),
                "COMPLETED".equals(jwt.getClaimAsString(TaskRecoveryConstant.REMOTE_STATUS)));
    }
}
