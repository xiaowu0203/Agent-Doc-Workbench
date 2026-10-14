package com.agentdoc.agent.service;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;

/** CONTROL 的配置观察，不创建执行或以授权人身份执行 Agent。 */
@Service
@RequiredArgsConstructor
public class OnlineConfigObservationService {
    private final AgentOnlineConfigService configs;
    private final OnlineEvaluationFeign evaluation;
    private final OnlineDocumentFeign document;
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;
    public String dependency(String experimentId, String token) {
        var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw denied(); }
        var jwt = verifier.verify(token, OnlineCapabilityPurpose.ONLINE_CONTROL, experimentId, List.of());
        var proof = evaluation.controlProof(experimentId, token); var permitted = document.requireProtectionPermission(experimentId, token);
        if (proof == null || proof.code() != ErrorCode.SUCCESS.getCode() || proof.data() == null
                || permitted == null || permitted.code() != ErrorCode.SUCCESS.getCode()) { throw denied(); }
        return configs.machineDependency(OnlineProtocolUtils.id(experimentId), OnlineProtocolUtils.id(jwt.getClaimAsString(JwtConstant.CLAIM_SPACE_ID)), token);
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.BINDING_INVALID.name()); }
}
