package com.agentdoc.evaluation.service;

import static com.agentdoc.common.enums.OnlineReasonCode.*;
import static com.agentdoc.evaluation.enums.OnlineExperimentStatus.*;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.enums.OnlineExperimentAction;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentActionRequestMapper;
import com.agentdoc.evaluation.mapper.OnlinePreflightProofMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentEventMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentActionRequestEntity;
import com.agentdoc.evaluation.pojo.entity.OnlinePreflightProofEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEventEntity;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentActionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** 状态、授权和请求结果在同一短事务提交；不执行 RPC。 */
@Service
@RequiredArgsConstructor
public class OnlineLifecyclePersistenceService {
    private final OnlineExperimentMapper experiments;
    private final OnlineExperimentActionRequestMapper actions;
    private final OnlinePreflightProofMapper proofs;
    private final OnlineExperimentEventMapper events;
    private final OnlineExecutionAuthority authority;

    public OnlineExperimentActionVO prior(Long experiment, Long actor, OnlineExperimentAction action, String key, String hash) {
        var row = actions.selectOne(new LambdaQueryWrapper<OnlineExperimentActionRequestEntity>()
                .eq(OnlineExperimentActionRequestEntity::getExperimentId, experiment).eq(OnlineExperimentActionRequestEntity::getActorId, actor)
                .eq(OnlineExperimentActionRequestEntity::getAction, action.name()).eq(OnlineExperimentActionRequestEntity::getRequestKey, key));
        if (row == null) { return null; }
        if (!hash.equals(row.getRequestHash())) { throw conflict(IDEMPOTENCY_CONFLICT.name()); }
        var result = JsonUtils.parseStrict(row.getResultJson(), OnlineExperimentActionVO.class);
        if (result == null) { throw conflict(MANIFEST_INVALID.name()); } return result;
    }

    @Transactional
    public OnlineExperimentActionVO apply(Long id, Long actor, OnlineExperimentAction action, String key, String hash,
            String manifestHash, Long version, String requestJson, String proofHash, String currentDependency, Grant grant) {
        var experiment = experiments.lock(id);
        if (experiment == null) { throw conflict(ONLINE_EXPERIMENT_NOT_FOUND.name()); }
        authority.manifest(experiment);
        var previous = prior(id, actor, action, key, hash); if (previous != null) { return previous; }
        if (!experiment.getManifestHash().equals(manifestHash) || !Objects.equals(experiment.getStateVersion(), version)) { throw conflict(ONLINE_STATE_CONFLICT.name()); }
        LocalDateTime now = LocalDateTime.now();
        switch (action) {
            case START, RESUME -> {
                if (action == OnlineExperimentAction.START && !CREATED.name().equals(experiment.getStatus())
                        || action == OnlineExperimentAction.RESUME && !PAUSED.name().equals(experiment.getStatus())
                        || experiment.getEmergencyStopRequestedAt() != null || grant == null) { throw conflict(ONLINE_GATE_CLOSED.name()); }
                if (action == OnlineExperimentAction.START) { requireProof(experiment, actor, proofHash, currentDependency); }
                if (action == OnlineExperimentAction.RESUME) { authority.requireResumeSafe(experiment); }
                if (!Objects.equals(currentDependency, authority.manifest(experiment).path("dependencyHash").asText())) { throw conflict(DEPENDENCY_DRIFT.name()); }
                if (experiment.getAssignmentDeadline() != null && !now.isBefore(experiment.getAssignmentDeadline())) { throw conflict(ONLINE_ASSIGNMENT_DEADLINE.name()); }
                if (experiment.getStartedAt() == null) {
                    var windows = authority.manifest(experiment).path("windowPlan"); experiment.setStartedAt(now);
                    experiment.setAssignmentDeadline(now.plusSeconds(windows.path("assignmentWindowSeconds").asLong()));
                    experiment.setObservationDeadline(experiment.getAssignmentDeadline().plusSeconds(windows.path("completionObservationSeconds").asLong()));
                }
                experiment.setActiveSlot(1); experiment.setStatus(ACTIVE.name()); authorize(experiment, actor, grant);
            }
            case PAUSE -> {
                if (!ACTIVE.name().equals(experiment.getStatus())) { throw conflict(ONLINE_GATE_CLOSED.name()); }
                experiment.setStatus(PAUSED.name());
            }
            case STOP, EMERGENCY_STOP -> {
                authority.closeGate(id, version, actor, action == OnlineExperimentAction.EMERGENCY_STOP, action.name());
                experiment = experiments.selectById(id);
            }
            case REAUTHORIZE -> {
                if (STOPPED.name().equals(experiment.getStatus()) || grant == null) { throw conflict(ONLINE_GATE_CLOSED.name()); }
                authorize(experiment, actor, grant);
            }
        }
        if (action != OnlineExperimentAction.STOP && action != OnlineExperimentAction.EMERGENCY_STOP) {
            experiment.setStateVersion(Math.incrementExact(experiment.getStateVersion())); experiment.setStateChangedBy(actor);
            experiment.setStateChangedAt(now); experiment.setReasonCode(action.name()); experiments.updateById(experiment);
            var event = new OnlineExperimentEventEntity(); event.setId(IdWorker.getId()); event.setExperimentId(id); event.setSpaceId(experiment.getSpaceId());
            event.setActorType("HUMAN"); event.setActorId(actor.toString()); event.setAuthorizedBy(actor); event.setEventType(action.name());
            event.setStateVersion(experiment.getStateVersion()); event.setReasonCode(action.name()); event.setDetailJson(requestJson); events.insert(event);
        } else {
            var event = new OnlineExperimentEventEntity(); event.setId(IdWorker.getId()); event.setExperimentId(id); event.setSpaceId(experiment.getSpaceId());
            event.setActorType("HUMAN"); event.setActorId(actor.toString()); event.setAuthorizedBy(actor); event.setEventType("HUMAN_STATE_REQUESTED");
            event.setStateVersion(experiment.getStateVersion()); event.setReasonCode(action.name()); event.setDetailJson(requestJson); events.insert(event);
        }
        var result = result(experiment);
        var request = new OnlineExperimentActionRequestEntity(); request.setId(IdWorker.getId()); request.setExperimentId(id); request.setActorId(actor);
        request.setAction(action.name()); request.setRequestKey(key); request.setRequestHash(hash); request.setRequestJson(requestJson);
        request.setResultJson(JsonUtils.toJson(result)); actions.insert(request);
        return result;
    }

    private void requireProof(OnlineExperimentEntity experiment, Long actor, String hash, String dependency) {
        var proof = proofs.selectOne(new LambdaQueryWrapper<OnlinePreflightProofEntity>().eq(OnlinePreflightProofEntity::getProofHash, hash));
        if (proof == null || !experiment.getId().equals(proof.getExperimentId()) || !actor.equals(proof.getActorId())
                || !experiment.getStateVersion().equals(proof.getStateVersion()) || !experiment.getManifestHash().equals(proof.getManifestHash())
                || !Objects.equals(dependency, proof.getDependencyHash()) || !proof.getExpiresAt().toInstant(ZoneOffset.UTC).isAfter(Instant.now())) { throw conflict(ONLINE_STATE_CONFLICT.name()); }
    }
    private static void authorize(OnlineExperimentEntity experiment, Long actor, Grant grant) {
        if (grant.expiresAt() == null || !grant.expiresAt().toInstant(ZoneOffset.UTC).isAfter(Instant.now())) { throw conflict(CANCEL_AUTHORIZATION_UNAVAILABLE.name()); }
        experiment.setControlAuthorizedBy(actor); experiment.setControlKeyVersion(grant.keyVersion());
        experiment.setControlCiphertext(grant.ciphertext()); experiment.setControlExpiresAt(grant.expiresAt());
    }
    private static OnlineExperimentActionVO result(OnlineExperimentEntity value) {
        return new OnlineExperimentActionVO(value.getId().toString(), value.getStatus(), value.getStateVersion().toString(), value.getManifestHash(),
                value.getReasonCode(), value.getActiveSlot(), time(value.getStartedAt()), time(value.getAssignmentDeadline()),
                time(value.getObservationDeadline()), time(value.getEmergencyStopRequestedAt()));
    }
    private static String time(LocalDateTime value) { return value == null ? null : value.toString(); }
    private static BusinessException conflict(String reason) { return new BusinessException(ErrorCode.CONFLICT, reason); }
    /** 加密在事务外完成。
     * @param keyVersion 加密密钥版本 @param ciphertext 密文 @param expiresAt UTC 截止 */
    public record Grant(String keyVersion, String ciphertext, LocalDateTime expiresAt) { }
}
