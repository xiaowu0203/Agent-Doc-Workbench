package com.agentdoc.auth.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.dto.OnlineDispatchIdentityDTO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;

/** 初次 WAIT 用实际创建者；执行权限只在 WAIT 交换且权威槽仍有效时签发。 */
@Service
@RequiredArgsConstructor
public class OnlineTaskCapabilityIssuanceService {
    private final JwtService jwt;
    private final SecurityVerifyProperties properties;
    private final OnlineCapabilityVerifier verifier;
    private final OnlineTaskFeign task;
    private final OnlineEvaluationFeign evaluation;

    public String initial(String taskId) {
        enabled(); OnlineProtocolUtils.id(taskId);
        var proof = data(task.humanDispatchProof(taskId));
        if (!taskId.equals(proof.binding().taskId()) || !AuthUtils.getUserIdOrException().toString().equals(proof.binding().actorId())
                || !"PENDING".equals(proof.taskStatus()) || !"LIVE".equals(proof.binding().executionMode())
                || !"ORIGINAL".equals(proof.binding().lineageType())) { throw denied(); }
        return jwt.createOnlineWait(proof);
    }
    public String exchange(String taskId, String wait) {
        enabled(); var source = verifier.verifyWait(wait, taskId);
        var proof = data(task.waitDispatchProof(taskId, wait));
        var permit = data(evaluation.permit(taskId, wait));
        if (!proof.binding().equals(permit.binding()) || !"PENDING".equals(proof.taskStatus())
                || !source.getExpiresAt().isAfter(Instant.now()) || proof.actions() == null || proof.actions().isEmpty()) { throw denied(); }
        var request = proof.request(); var binding = proof.binding();
        var identity = new OnlineDispatchIdentityDTO(binding.experimentId(), binding.assignmentId(), binding.schemaVersion(),
                permit.bindingHash(), permit.generation(), permit.permitHash());
        return jwt.createTaskCapabilityToken(OnlineProtocolUtils.id(taskId), OnlineProtocolUtils.id(binding.agentId()),
                OnlineProtocolUtils.id(binding.spaceId()), OnlineProtocolUtils.id(binding.documentId()), binding.executionMode(),
                OnlineProtocolUtils.id(request.documentVersion()), request.documentContentHash(), request.inputSchemaVersion(), request.inputHash(),
                null, proof.actions(), identity);
    }
    /** 只能用尚未过期的 WAIT 延续同一排队证明；源到期后必须回到原创建者的人类入口。 */
    public String renew(String taskId, String wait) {
        enabled(); var source = verifier.verifyWait(wait, taskId);
        var proof = data(task.waitDispatchProof(taskId, wait)); var binding = data(evaluation.waiting(taskId, wait));
        if (!"PENDING".equals(proof.taskStatus()) || !binding.equals(proof.binding())
                || !source.getExpiresAt().isAfter(Instant.now())) { throw denied(); }
        return jwt.createOnlineWait(proof);
    }
    private void enabled() {
        if (!properties.isOnlineCapabilityEnabled() || !properties.getOnlineCapabilityIssuer().equals(jwt.props().issuer())) { throw denied(); }
    }
    private static <T> T data(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) { throw denied(); } return result.data();
    }
    private static BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.BINDING_INVALID.name()); }
}
