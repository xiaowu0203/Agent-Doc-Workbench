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
