package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;

/** 开始执行前复核有限控制授权及授权人的当前保护权限；失败不释放任何槽或额度。 */
@Service
@RequiredArgsConstructor
public class OnlineControlAccessService {
    private final OnlineExperimentMapper experiments;
    private final OnlineAuthorizationProofService proofs;
    private final OnlineExecutionAuthority authority;
    private final WorkerCapabilityCryptoService crypto;
    private final OnlineDocumentFeign document;
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;

    public String require(Long experimentId) {
        try {
            var experiment = experiments.selectById(experimentId);
            if (experiment == null || experiment.getControlCiphertext() == null || experiment.getControlAuthorizedBy() == null) { throw denied(); }
            String token = crypto.decrypt(experiment.getControlKeyVersion(), experiment.getControlCiphertext());
            var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw denied(); }
            verifier.verify(token, OnlineCapabilityPurpose.ONLINE_CONTROL, experimentId.toString(), List.of());
            proofs.control(experimentId.toString(), token);
            var permitted = document.requireProtectionPermission(experimentId.toString(), token);
            if (permitted == null || permitted.code() != ErrorCode.SUCCESS.getCode()) { throw denied(); }
            return token;
        } catch (RuntimeException unavailable) {
            authority.safetyPause(experimentId, OnlineReasonCode.CANCEL_AUTHORIZATION_UNAVAILABLE.name());
            throw denied();
        }
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.CANCEL_AUTHORIZATION_UNAVAILABLE.name()); }
}
