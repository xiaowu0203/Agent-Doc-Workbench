package com.agentdoc.document.service;

import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import static com.agentdoc.common.enums.OnlineReasonCode.CANCEL_AUTHORIZATION_UNAVAILABLE;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.document.enums.DocStatus;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_EDIT;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;

/** 专用只读权限证明入口；没有文档正文或写能力。 */
@Service
@RequiredArgsConstructor
public class OnlineProtectionPermissionService {
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;
    private final SpacePermissionService permissions;
    private final OnlineEvaluationFeign evaluation;
    private final DocumentService documents;
    /** 当前用户和冻结来源双重授权；只核验无暂存，不用 OBSERVE 执行任何文档写入。 */
    public void requireNoDraftStaging(String taskId) {
        long userId = AuthUtils.getUserIdOrException();
        var result = evaluation.humanBinding(taskId);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "RECOVERY_IDENTITY_MISMATCH");
        }
        var binding = result.data(); long spaceId = OnlineProtocolUtils.id(binding.spaceId());
        if (!taskId.equals(binding.taskId()) || userId != OnlineProtocolUtils.id(binding.actorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "RECOVERY_IDENTITY_MISMATCH");
        }
        permissions.requireOnlineProtectionPermission(spaceId, userId);
        permissions.requirePermission(spaceId, DOCUMENT_EDIT);
        var doc = documents.requireDoc(OnlineProtocolUtils.id(binding.documentId()));
        if (!Long.valueOf(spaceId).equals(doc.getSpaceId()) || !Integer.valueOf(DocStatus.NORMAL.getCode()).equals(doc.getStatus())
                || !Integer.valueOf(DocType.DRAFT.getCode()).equals(doc.getDocType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "RECOVERY_IDENTITY_MISMATCH");
        }
        documents.requireOnlineFrozenVersion(doc.getId(), OnlineProtocolUtils.documentVersion(binding.documentVersion()), binding.documentContentHash());
        if (doc.getAgentStagedTaskId() != null || doc.getAgentStagedBaseVersion() != null
                || doc.getAgentStagedRevision() != null || doc.getAgentStagedContent() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "DRAFT_VERSION_CONFLICT");
        }
    }
    public void resources(String experimentId, String token) {
        require(experimentId, token);
        var scope = evaluation.resourceScope(experimentId, token);
        if (scope == null || scope.code() != ErrorCode.SUCCESS.getCode() || scope.data() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, CANCEL_AUTHORIZATION_UNAVAILABLE.name());
        }
        documents.requireOnlineResources(OnlineProtocolUtils.id(scope.data().spaceId()), scope.data().documentIds().stream().map(OnlineProtocolUtils::id).toList());
    }
    public void requireHuman(String spaceId) {
        permissions.requireOnlineProtectionPermission(OnlineProtocolUtils.id(spaceId), AuthUtils.getUserIdOrException());
    }
    public void require(String experimentId, String token) {
        require(experimentId, token, OnlineCapabilityPurpose.ONLINE_CONTROL, List.of());
    }
    public void require(String experimentId, String token, OnlineCapabilityPurpose purpose, List<String> taskIds) {
        var verifier = verifiers.getIfAvailable();
        if (verifier == null) { throw new BusinessException(ErrorCode.FORBIDDEN, CANCEL_AUTHORIZATION_UNAVAILABLE.name()); }
        Jwt proof;
        try { proof = verifier.verify(token, purpose, experimentId, taskIds); }
        catch (JwtException invalid) { throw new BusinessException(ErrorCode.FORBIDDEN, CANCEL_AUTHORIZATION_UNAVAILABLE.name()); }
        permissions.requireOnlineProtectionPermission(OnlineProtocolUtils.id(proof.getClaimAsString(JwtConstant.CLAIM_SPACE_ID)),
                OnlineProtocolUtils.id(proof.getClaimAsString(AUTHORIZED_BY)));
    }
}
