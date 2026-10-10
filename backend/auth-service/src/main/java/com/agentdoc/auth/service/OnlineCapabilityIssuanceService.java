package com.agentdoc.auth.service;

import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import static com.agentdoc.common.enums.OnlineReasonCode.CANCEL_AUTHORIZATION_UNAVAILABLE;
import static com.agentdoc.common.enums.TaskExecutionMode.LIVE;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.dto.OnlineCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.OnlineControlAuthorizeDTO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineAuthorizationProofVO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.time.Instant;

/** 固定用途签发；网络查证在事务外，既有离线签发不获 LIVE 权限。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnlineCapabilityIssuanceService {
    private final JwtService jwtService;
    private final SecurityVerifyProperties properties;
    private final OnlineCapabilityVerifier verifier;
    private final OnlineDocumentFeign onlineDocument;
    private final OnlineEvaluationFeign evaluation;

    public String authorize(OnlineControlAuthorizeDTO request) {
        requireEnabled();
        String actor = AuthUtils.getUserIdOrException().toString();
        if (request == null || !request.automaticCancellationAcknowledged()) { throw denied(); }
        OnlineProtocolUtils.id(request.experimentId());
        var proof = data(evaluation.humanProof(request.experimentId()));
        // 当前 OWNER 可重新明确授权；新凭证仍须由状态应用层确认授权人后才能派生/使用。
        requireProof(proof, request.experimentId(), request.manifestHash(), null);
        success(onlineDocument.requireHumanProtectionPermission(proof.spaceId()));
        return sign(proof, actor, OnlineCapabilityPurpose.ONLINE_CONTROL, null, null);
    }

    public String issue(String control, OnlineCapabilityIssueDTO request) {
        requireEnabled();
        if (request == null || request.purpose() == null) { throw denied(); }
        Jwt source;
        try { source = verifier.verify(control, OnlineCapabilityPurpose.ONLINE_CONTROL, null, List.of()); }
        catch (RuntimeException invalid) { throw denied(); }
        String experimentId = source.getClaimAsString(EXPERIMENT_ID);
        String actor = source.getClaimAsString(AUTHORIZED_BY);
        var proof = data(evaluation.controlProof(experimentId, control));
        requireProof(proof, experimentId, source.getClaimAsString(MANIFEST_HASH), actor);
        if (!proof.spaceId().equals(source.getClaimAsString(JwtConstant.CLAIM_SPACE_ID))) { throw denied(); }
        success(onlineDocument.requireProtectionPermission(experimentId, control));
        String taskHash = null;
        if (request.purpose() == OnlineCapabilityPurpose.ONLINE_CONTROL) {
            if (request.taskIds() != null && !request.taskIds().isEmpty()) { throw denied(); }
        } else {
            List<String> ids;
            try { ids = OnlineCapabilityUtils.taskIds(request.taskIds()); }
            catch (IllegalArgumentException invalid) { throw denied(); }
            var bindings = data(evaluation.bindings(experimentId, control, ids));
            if (bindings.size() != ids.size() || !ids.equals(bindings.stream().map(OnlineTaskBindingDTO::taskId).toList())) { throw denied(); }
            for (var binding : bindings) {
                if (binding.schemaVersion() != OnlineProtocolUtils.SCHEMA_VERSION
                        || !proof.experimentId().equals(binding.experimentId()) || !proof.spaceId().equals(binding.spaceId())
                        || !proof.manifestHash().equals(binding.manifestHash())
                        || !LIVE.name().equals(binding.executionMode()) || !ORIGINAL_LINEAGE.equals(binding.lineageType())) { throw denied(); }
            }
            taskHash = OnlineCapabilityUtils.taskSetHash(ids);
        }
        // 查证不能把过期源凭证续活；尤其不能利用网络耗时跨越 TTL。
        if (!source.getExpiresAt().isAfter(Instant.now())) { throw denied(); }
        return sign(proof, actor, request.purpose(), taskHash, source);
    }

    private String sign(OnlineAuthorizationProofVO proof, String actor, OnlineCapabilityPurpose purpose, String taskHash, Jwt source) {
        String token;
        try { token = jwtService.createOnlineCapability(proof, actor, purpose, taskHash, source == null ? null : source.getExpiresAt()); }
        catch (IllegalArgumentException expired) { throw denied(); }
        log.info("线上窄授权签发，actorType=SERVICE，authorizedBy={}，experimentId={}，purpose={}", actor, proof.experimentId(), purpose);
        return token;
    }

    private void requireProof(OnlineAuthorizationProofVO proof, String id, String manifest, String actor) {
        if (!id.equals(proof.experimentId()) || proof.schemaVersion() != OnlineProtocolUtils.SCHEMA_VERSION
                || manifest == null || !manifest.matches("[0-9a-f]{64}") || !manifest.equals(proof.manifestHash())
                || !AUTHORIZABLE_STATUSES.contains(proof.status())
                || actor != null && !Objects.equals(actor, proof.authorizedBy())) { throw denied(); }
        OnlineProtocolUtils.id(proof.spaceId());
    }
    private void requireEnabled() {
        if (!properties.isOnlineCapabilityEnabled() || !properties.getOnlineCapabilityIssuer().equals(jwtService.props().issuer())) { throw denied(); }
    }
    private void success(Result<?> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) { throw denied(); }
    }
    private <T> T data(Result<T> result) {
        success(result);
        if (result.data() == null) { throw denied(); }
        return result.data();
    }
    private BusinessException denied() { return new BusinessException(ErrorCode.FORBIDDEN, CANCEL_AUTHORIZATION_UNAVAILABLE.name()); }
}
