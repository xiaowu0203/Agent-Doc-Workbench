package com.agentdoc.evaluation.service;

import static com.agentdoc.common.enums.OnlineReasonCode.*;
import static com.agentdoc.common.constant.SpacePermissionConstant.*;
import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineAuthFeign;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.feign.dto.OnlineControlAuthorizeDTO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.enums.OnlineExperimentAction;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentStartDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentStateDTO;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentActionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 人类状态入口：RPC 与密文准备都在短事务前，完整预检不能被布尔开关绕过。 */
@Service
@RequiredArgsConstructor
public class OnlineExperimentLifecycleService {
    private final OnlineExperimentMapper experiments;
    private final OnlineExperimentService queries;
    private final OnlineExecutionAuthority authority;
    private final OnlineLifecyclePersistenceService persistence;
    private final SpaceAccessService access;
    private final OnlineAuthFeign auth;
    private final WorkerCapabilityCryptoService crypto;
    private final ObjectProvider<OnlineCapabilityVerifier> verifiers;

    public OnlineExperimentActionVO start(String id, OnlineExperimentStartDTO request) {
        if (request == null || !request.liveSideEffectsAcknowledged() || !request.retentionAcknowledged()
                || !request.sharedResourcesAcknowledged() || !request.emergencyCancellationAcknowledged()) { throw invalid(); }
        validate(request.clientRequestKey(), request.manifestHash(), request.expectedStateVersion());
        var experiment = require(id); authorize(experiment); Long actor = AuthUtils.getUserIdOrException();
        String hash = hash(id, actor, OnlineExperimentAction.START, request);
        var prior = persistence.prior(experiment.getId(), actor, OnlineExperimentAction.START, request.clientRequestKey(), hash);
        if (prior != null) { return prior; }
        var checked = queries.preflight(id);
        if (!checked.startable()) { throw new BusinessException(ErrorCode.CONFLICT, ONLINE_EXECUTION_NOT_READY.name()); }
        return persistence.apply(experiment.getId(), actor, OnlineExperimentAction.START, request.clientRequestKey(), hash, request.manifestHash(),
                version(request.expectedStateVersion()), JsonUtils.toJson(request), request.preflightProofHash(),
                authority.manifest(experiment).path("dependencyHash").asText(), grant(experiment));
    }
    public OnlineExperimentActionVO state(String id, OnlineExperimentAction action, OnlineExperimentStateDTO request) {
        if (action == null || action == OnlineExperimentAction.START || request == null || request.reason() == null
                || request.reason().trim().isEmpty() || request.reason().codePointCount(0, request.reason().length()) > 2000) { throw invalid(); }
        validate(request.clientRequestKey(), request.manifestHash(), request.expectedStateVersion());
        var experiment = require(id); authorize(experiment); Long actor = AuthUtils.getUserIdOrException();
        String hash = hash(id, actor, action, request);
        var prior = persistence.prior(experiment.getId(), actor, action, request.clientRequestKey(), hash); if (prior != null) { return prior; }
        OnlineLifecyclePersistenceService.Grant grant = null; String dependency = null;
        if (action == OnlineExperimentAction.RESUME) {
            var checked = queries.preflight(id);
            if (!checked.startable()) { throw new BusinessException(ErrorCode.CONFLICT, ONLINE_EXECUTION_NOT_READY.name()); }
            dependency = authority.manifest(experiment).path("dependencyHash").asText(); grant = grant(experiment);
        } else if (action == OnlineExperimentAction.REAUTHORIZE) { grant = grant(experiment); }
        // emergency 必须先关门；取消是否成功由窄授权接收方与权威对账单独决定。
        return persistence.apply(experiment.getId(), actor, action, request.clientRequestKey(), hash, request.manifestHash(),
                version(request.expectedStateVersion()), JsonUtils.toJson(request), null, dependency, grant);
    }
    private void authorize(OnlineExperimentEntity experiment) { access.requireOwner(experiment.getSpaceId()); access.requirePermission(experiment.getSpaceId(), EVALUATION_RUN); }
    private OnlineLifecyclePersistenceService.Grant grant(OnlineExperimentEntity experiment) {
        var result = auth.authorize(new OnlineControlAuthorizeDTO(experiment.getId().toString(), experiment.getManifestHash(), true));
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) { throw invalid(); }
        var verifier = verifiers.getIfAvailable(); if (verifier == null) { throw invalid(); }
        var jwt = verifier.verify(result.data(), OnlineCapabilityPurpose.ONLINE_CONTROL, experiment.getId().toString(), List.of());
        if (!experiment.getManifestHash().equals(jwt.getClaimAsString(MANIFEST_HASH))
                || !AuthUtils.getUserIdOrException().toString().equals(jwt.getClaimAsString(AUTHORIZED_BY))
                || !experiment.getSpaceId().toString().equals(jwt.getClaimAsString(JwtConstant.CLAIM_SPACE_ID))) { throw invalid(); }
        var encrypted = crypto.encrypt(result.data());
        return new OnlineLifecyclePersistenceService.Grant(encrypted.keyVersion(), encrypted.ciphertext(), LocalDateTime.ofInstant(jwt.getExpiresAt(), ZoneOffset.UTC));
    }
    private OnlineExperimentEntity require(String id) {
        var experiment = experiments.selectById(OnlineProtocolUtils.id(id));
        if (experiment == null) { throw new BusinessException(ErrorCode.NOT_FOUND, ONLINE_EXPERIMENT_NOT_FOUND.name()); }
        authority.manifest(experiment); return experiment;
    }
    private static void validate(String key, String manifest, String version) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,64}") || manifest == null || !manifest.matches("[0-9a-f]{64}")) { throw invalid(); }
        version(version);
    }
    private static long version(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) { throw invalid(); }
        try { return Long.parseLong(value); } catch (NumberFormatException invalid) { throw invalid(); }
    }
    private static String hash(String id, Long actor, OnlineExperimentAction action, Object request) {
        return OnlineProtocolUtils.hash("online.action-request", Map.of("experimentId", id, "actorId", actor.toString(), "action", action.name(), "request", request));
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.BAD_REQUEST, MANIFEST_INVALID.name()); }
}
