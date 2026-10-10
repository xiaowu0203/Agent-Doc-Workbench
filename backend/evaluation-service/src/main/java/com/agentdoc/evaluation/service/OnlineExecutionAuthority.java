package com.agentdoc.evaluation.service;

import static com.agentdoc.common.enums.OnlineReasonCode.*;
import static com.agentdoc.evaluation.constant.OnlineSafetyConstant.*;
import static com.agentdoc.evaluation.constant.OnlineExperimentConstant.*;
import static com.agentdoc.evaluation.enums.OnlineExperimentStatus.*;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineSlotPermitVO;
import com.agentdoc.common.feign.vo.OnlineTaskFactVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExecutionSlotMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentEventMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExecutionSlotEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEventEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 短事务安全内核：所有写操作先锁实验，再锁槽，最后锁分配，不持锁执行 RPC。
 * 调用方必须先完成权限、发布依赖及事实来源验证；公开启动仍受完整预检约束。
 */
@Service
@RequiredArgsConstructor
public class OnlineExecutionAuthority {
    private final OnlineExperimentMapper experiments;
    private final OnlineAssignmentMapper assignments;
    private final OnlineExecutionSlotMapper slots;
    private final OnlineExperimentEventMapper events;

    /** 拒绝以结果返回，确保预算/截止自动关门能够提交；调用方在事务返回后转为业务错误。 */
    @Transactional
    public Allocation allocate(Long experimentId, OnlineAssignmentRequestDTO request) {
        validate(request);
        var experiment = lock(experimentId);
        var prior = assignments.selectOne(new LambdaQueryWrapper<OnlineAssignmentEntity>()
                .eq(OnlineAssignmentEntity::getSpaceId, OnlineProtocolUtils.id(request.spaceId()))
                .eq(OnlineAssignmentEntity::getCreatedBy, OnlineProtocolUtils.id(request.actorId()))
                .eq(OnlineAssignmentEntity::getClientRequestKey, request.requestKey()));
        if (prior != null) {
            if (!experimentId.equals(prior.getExperimentId()) || !request.requestHash().equals(prior.getRequestHash())
                    || !request.inputHash().equals(prior.getInputSnapshotHash())
                    || !request.taskId().equals(prior.getTaskId().toString())
                    || !request.equals(requestOf(binding(prior), prior))) { throw conflict(IDEMPOTENCY_CONFLICT); }
            return new Allocation(binding(prior), null);
        }
        JsonNode manifest = manifest(experiment);
        if (!request.spaceId().equals(experiment.getSpaceId().toString())
                || !request.agentId().equals(experiment.getAgentId().toString())
                || !containsDocument(manifest, request.documentId())) { throw conflict(SCOPE_INVALID); }
        if (!ACTIVE.name().equals(experiment.getStatus())) { return new Allocation(null, ONLINE_GATE_CLOSED.name()); }
        requireLiveControl(experiment);
        LocalDateTime now = LocalDateTime.now();
        if (experiment.getAssignmentDeadline() == null || !now.isBefore(experiment.getAssignmentDeadline())) {
            change(experiment, STOPPING.name(), ONLINE_ASSIGNMENT_DEADLINE.name(), null, false, now);
            finishIfSettled(experiment, now); experiments.updateById(experiment);
            return new Allocation(null, ONLINE_ASSIGNMENT_DEADLINE.name());
        }
        long budget = OnlineProtocolUtils.id(request.tokenBudget());
        if (budget > OnlineProtocolUtils.id(manifest.path("budgetPlan").path("perTask").asText())) {
            throw conflict(MANIFEST_INVALID);
        }
        BigInteger total = experiment.getConsumedTokens().add(BigInteger.valueOf(experiment.getReservedTokenBudget()))
                .add(BigInteger.valueOf(budget));
        if (experiment.getAssignedTaskCount() >= experiment.getMaxTaskCount()
                || total.compareTo(BigInteger.valueOf(experiment.getAuthorizedTokenBudget())) > 0) {
            change(experiment, PAUSED.name(), ONLINE_BUDGET_EXHAUSTED.name(), null, false, now);
            return new Allocation(null, ONLINE_BUDGET_EXHAUSTED.name());
        }
        long sequence = Math.incrementExact(experiment.getAcceptedSequence());
        int bucket = OnlineProtocolUtils.bucket(experimentId.toString(), request.documentId(),
                manifest.path("bucketProtocol").path("seed").asText());
        String variant = OnlineProtocolUtils.variant(bucket, manifest.path("bucketProtocol").path("weight").asInt());
        JsonNode template = manifest.path(variant.toLowerCase(Locale.ROOT));
        var binding = new OnlineTaskBindingDTO(Long.toString(IdWorker.getId()), experimentId.toString(),
                experiment.getManifestHash(), Long.toString(sequence), variant, bucket, request.actorId(), request.taskId(),
                request.spaceId(), request.agentId(), request.documentId(), request.documentVersion(), request.documentContentHash(),
                request.inputSchemaVersion(), request.inputHash(), template.path("id").asText(), template.path("schemaVersion").asInt(),
                template.path("hash").asText(), template.path("nonPromptHash").asText(), manifest.path("dependencyHash").asText(),
                request.tokenBudget(), manifest.path("budgetPlan").path("timeout").asInt(), LIVE, ORIGINAL, OnlineProtocolUtils.SCHEMA_VERSION);
        var assignment = new OnlineAssignmentEntity();
        assignment.setId(OnlineProtocolUtils.id(binding.assignmentId()));
        assignment.setExperimentId(experimentId); assignment.setSpaceId(experiment.getSpaceId()); assignment.setAgentId(experiment.getAgentId());
        assignment.setTaskId(OnlineProtocolUtils.id(request.taskId())); assignment.setDocumentId(OnlineProtocolUtils.id(request.documentId()));
        assignment.setCreatedBy(OnlineProtocolUtils.id(request.actorId())); assignment.setClientRequestKey(request.requestKey());
        assignment.setRequestHash(request.requestHash()); assignment.setInputSnapshotSchemaVersion(request.inputSchemaVersion());
        assignment.setInputSnapshotHash(request.inputHash()); assignment.setBucket(bucket); assignment.setVariant(variant);
        assignment.setTemplateId(OnlineProtocolUtils.id(binding.templateId())); assignment.setConfigSchemaVersion(binding.templateSchemaVersion());
        assignment.setConfigHash(binding.templateHash()); assignment.setDependencyHash(binding.dependencyHash());
        assignment.setBindingSchemaVersion(OnlineProtocolUtils.SCHEMA_VERSION); assignment.setBindingHash(OnlineProtocolUtils.hash("online.binding", binding));
        assignment.setBindingJson(OnlineProtocolUtils.canonical("online.binding", binding)); assignment.setAcceptedSequence(sequence);
        assignment.setReservedTokenBudget(budget); assignment.setTaskConfirmationStatus(PENDING); assignment.setDispatchStatus(PENDING);
        assignment.setSlotStatus(WAITING); assignment.setCancelStatus(NONE); assignment.setSettlementStatus(RESERVED);
        assignments.insert(assignment);
        experiment.setAcceptedSequence(sequence); experiment.setAssignedTaskCount(experiment.getAssignedTaskCount() + 1);
        experiment.setReservedTokenBudget(Math.addExact(experiment.getReservedTokenBudget(), budget)); experiments.updateById(experiment);
        event(experiment, "ASSIGNMENT_ACCEPTED", assignment.getCreatedBy(), assignment.getId(), null);
        return new Allocation(binding, null);
    }

    /** confirmedTaskHash 只能来自 Task 权威确认；不能把客户端自报绑定作为落库证明。 */
    @Transactional
    public OnlineSlotPermitVO claim(Long experimentId, Long taskId, String confirmedTaskHash) {
        var experiment = lock(experimentId);
        requireLiveControl(experiment);
        var projection = requireAssignment(experimentId, taskId);
        var slot = slot(experiment, projection.getVariant());
        var assignment = assignments.lockTask(taskId);
        if (!experiment.getManifestHash().equals(binding(assignment).manifestHash())) { throw conflict(MANIFEST_INVALID); }
        if (!assignment.getBindingHash().equals(confirmedTaskHash)) { throw conflict(ONLINE_SLOT_INVALID); }
        if (experiment.getEmergencyStopRequestedAt() != null || CREATED.name().equals(experiment.getStatus()) || STOPPED.name().equals(experiment.getStatus())
                || SETTLED.equals(assignment.getSettlementStatus()) || assignment.getExecutionTerminalAt() != null) {
            throw conflict(ONLINE_GATE_CLOSED);
        }
        if (slot.getTaskId() != null) {
            if (!taskId.equals(slot.getTaskId())) { throw conflict(ONLINE_SLOT_BUSY); }
            return permit(slot, assignment);
        }
        slot.setGeneration(Math.incrementExact(slot.getGeneration())); slot.setAssignmentId(assignment.getId()); slot.setTaskId(taskId);
        slot.setBindingHash(assignment.getBindingHash()); slot.setAcquiredAt(LocalDateTime.now()); slot.setStartedAt(null);
        slots.updateById(slot);
        assignment.setTaskConfirmationStatus(CONFIRMED); assignment.setTaskConfirmedAt(LocalDateTime.now()); assignment.setSlotStatus(ACQUIRED);
        assignments.updateById(assignment);
        countSlots(experiment, assignment.getVariant(), 1); experiments.updateById(experiment);
        event(experiment, "SLOT_ACQUIRED", assignment.getCreatedBy(), assignment.getId(), null);
        return permit(slot, assignment);
    }

    /** 开始与紧急停止在同一实验锁上排序；正常暂停/停止允许已接受项继续。 */
    @Transactional
    public OnlineSlotPermitVO begin(Long experimentId, Long taskId, long generation, String permitHash) {
        var experiment = lock(experimentId);
        requireLiveControl(experiment);
        var projection = requireAssignment(experimentId, taskId);
        var slot = slots.lock(experimentId, projection.getVariant());
        var assignment = assignments.lockTask(taskId);
        if (!experiment.getManifestHash().equals(binding(assignment).manifestHash())) { throw conflict(MANIFEST_INVALID); }
        if (slot == null || !taskId.equals(slot.getTaskId()) || generation != slot.getGeneration()
                || !Objects.equals(slot.getBindingHash(), assignment.getBindingHash())
                || !permit(slot, assignment).permitHash().equals(permitHash)) { throw conflict(ONLINE_SLOT_INVALID); }
        if (experiment.getEmergencyStopRequestedAt() != null || STOPPED.name().equals(experiment.getStatus())) {
            throw conflict(ONLINE_GATE_CLOSED);
        }
        if (slot.getStartedAt() == null) {
            slot.setStartedAt(LocalDateTime.now()); slots.updateById(slot);
            assignment.setDispatchStatus("STARTED"); assignments.updateById(assignment);
            event(experiment, "EXECUTION_STARTED", assignment.getCreatedBy(), assignment.getId(), null);
        }
        return permit(slot, assignment);
    }

    /** 只写取消请求，不声称远端终止或释放执行槽。权限复核由外层应用服务负责。 */
    @Transactional
    public void closeGate(Long experimentId, Long expectedVersion, Long actorId, boolean emergency, String reason) {
        var experiment = lock(experimentId);
        if (!Objects.equals(expectedVersion, experiment.getStateVersion())) { throw conflict(ONLINE_STATE_CONFLICT); }
        if (!CREATED.name().equals(experiment.getStatus()) && !ACTIVE.name().equals(experiment.getStatus()) && !PAUSED.name().equals(experiment.getStatus())
                && !STOPPING.name().equals(experiment.getStatus())) { throw conflict(ONLINE_GATE_CLOSED); }
        change(experiment, STOPPING.name(), reason, actorId, emergency, LocalDateTime.now());
        finishIfSettled(experiment, LocalDateTime.now()); experiments.updateById(experiment);
    }

    /** 保护失效关闭新分配，保存 SERVICE 事实，不清理占位、槽或未知预留。 */
    @Transactional
    public void safetyPause(Long experimentId, String reason) {
        var experiment = lock(experimentId);
        if (ACTIVE.name().equals(experiment.getStatus())) { change(experiment, PAUSED.name(), reason, null, false, LocalDateTime.now()); }
    }

    /** 只对处理前首次接受文档计数；重试 Task 不重复增加实验单位，五分钟内不重复检验。 */
    @Transactional
    public void runtimeSrm(Long experimentId) {
        var experiment = lock(experimentId); LocalDateTime now = LocalDateTime.now();
        if (!ACTIVE.name().equals(experiment.getStatus()) || experiment.getLastRuntimeSrmCheckAt() != null
                && now.isBefore(experiment.getLastRuntimeSrmCheckAt().plusSeconds(UNRESOLVED_SECONDS))) { return; }
        var units = assignments.runtimeUnits(experimentId);
        int candidate = (int) units.stream().filter("CANDIDATE"::equals).count();
        int weight = manifest(experiment).path("bucketProtocol").path("weight").asInt();
        var result = OnlineExperimentStatistics.srm(units.size(), candidate, weight);
        var previous = events.selectOne(new LambdaQueryWrapper<OnlineExperimentEventEntity>()
                .eq(OnlineExperimentEventEntity::getExperimentId, experimentId)
                .eq(OnlineExperimentEventEntity::getEventType, "RUNTIME_SRM_CHECKED")
                .orderByDesc(OnlineExperimentEventEntity::getId).last("LIMIT 1"));
        experiment.setLastRuntimeSrmCheckAt(now);
        if ("DETECTED".equals(result.status()) && previous != null && "DETECTED".equals(previous.getReasonCode())) {
            change(experiment, PAUSED.name(), SRM_DETECTED.name(), null, false, now);
        }
        experiments.updateById(experiment);
        var evidence = new OnlineExperimentEventEntity(); evidence.setId(IdWorker.getId()); evidence.setExperimentId(experimentId);
        evidence.setSpaceId(experiment.getSpaceId()); evidence.setActorType("SERVICE"); evidence.setActorId(JwtConstant.EVALUATION_SERVICE);
        evidence.setAuthorizedBy(experiment.getControlAuthorizedBy()); evidence.setEventType("RUNTIME_SRM_CHECKED");
        evidence.setStateVersion(experiment.getStateVersion()); evidence.setReasonCode(result.status());
        evidence.setDetailJson(JsonUtils.toJson(Map.of("documentCount", units.size(), "candidateDocumentCount", candidate,
                "result", result))); events.insert(evidence);
    }

    /** 已验签续签的 CAS 保存；迟到的旧授权不能覆盖新的人工重新授权。 */
    @Transactional
    public void renewedControl(Long experimentId, String previousCiphertext, Long authorizedBy, WorkerCapabilityCryptoService.EncryptedCapability next,
            LocalDateTime expiresAt) {
        var experiment = lock(experimentId);
        if (!Objects.equals(previousCiphertext, experiment.getControlCiphertext()) || !Objects.equals(authorizedBy, experiment.getControlAuthorizedBy())
                || STOPPED.name().equals(experiment.getStatus())) { return; }
        experiment.setControlKeyVersion(next.keyVersion()); experiment.setControlCiphertext(next.ciphertext()); experiment.setControlExpiresAt(expiresAt);
        experiments.updateById(experiment); event(experiment, "CONTROL_RENEWED", null, null, null);
    }

    /** 必须合并两个独立权威来源：Task/Token账本与Agent实际执行。未经查证的缺失不补零。 */
    @Transactional
    public void reconcile(Long experimentId, Long taskId, OnlineTaskFactVO taskFact, OnlineTaskFactVO executionFact) {
        var experiment = lock(experimentId);
        var projection = requireAssignment(experimentId, taskId);
        var slot = slots.lock(experimentId, projection.getVariant());
        var assignment = assignments.lockTask(taskId);
        if (!experiment.getManifestHash().equals(binding(assignment).manifestHash())) { throw conflict(MANIFEST_INVALID); }
        verifyFact(assignment, taskFact); verifyFact(assignment, executionFact);
        LocalDateTime now = LocalDateTime.now();
        assignment.setLastObservedAt(now);
        if (taskFact != null && executionFact != null && taskFact.executionId() != null && executionFact.executionId() != null
                && !taskFact.executionId().equals(executionFact.executionId())) { throw conflict(ONLINE_SLOT_INVALID); }
        boolean terminal = executionFact != null && executionFact.executionStatus() != null && EXECUTION_TERMINAL.contains(executionFact.executionStatus());
        boolean provenZero = taskFact != null && executionFact != null && taskFact.neverDispatched()
                && "TERMINATED".equals(taskFact.taskStatus()) && "ABSENT".equals(executionFact.executionStatus())
                && executionFact.executionId() == null
                && taskFact.executionId() == null && assignment.getExecutionId() == null && assignment.getExecutionTerminalAt() == null
                && PENDING.equals(assignment.getDispatchStatus())
                && (slot == null || !taskId.equals(slot.getTaskId()) || slot.getStartedAt() == null);
        if (terminal) {
            if (executionFact.executionId() == null || executionFact.executionFinishedAt() == null) { throw conflict(ONLINE_SLOT_INVALID); }
            long executionId = OnlineProtocolUtils.id(executionFact.executionId());
            if (assignment.getExecutionId() != null && !assignment.getExecutionId().equals(executionId)) { throw conflict(ONLINE_SLOT_INVALID); }
            assignment.setExecutionId(executionId); assignment.setExecutionStatus(executionFact.executionStatus());
            LocalDateTime finishedAt;
            try { finishedAt = LocalDateTime.parse(executionFact.executionFinishedAt()).truncatedTo(ChronoUnit.MILLIS); }
            catch (DateTimeParseException invalid) { throw conflict(ONLINE_SLOT_INVALID); }
            if (assignment.getExecutionTerminalAt() != null && !finishedAt.equals(assignment.getExecutionTerminalAt())) { throw conflict(ONLINE_SLOT_INVALID); }
            assignment.setExecutionTerminalAt(finishedAt);
        }
        if ((terminal || provenZero) && slot != null && taskId.equals(slot.getTaskId())) {
            // MyBatis 默认忽略 null，必须显式 SET NULL，保留 generation 防止旧证明复活。
            slots.update(null, new LambdaUpdateWrapper<OnlineExecutionSlotEntity>().eq(OnlineExecutionSlotEntity::getId, slot.getId())
                    .set(OnlineExecutionSlotEntity::getTaskId, null).set(OnlineExecutionSlotEntity::getAssignmentId, null)
                    .set(OnlineExecutionSlotEntity::getBindingHash, null).set(OnlineExecutionSlotEntity::getAcquiredAt, null)
                    .set(OnlineExecutionSlotEntity::getStartedAt, null));
            assignment.setSlotStatus(RELEASED); countSlots(experiment, assignment.getVariant(), -1);
            event(experiment, "SLOT_RELEASED", null, assignment.getId(), null);
        }
        BigInteger tokens = provenZero ? BigInteger.ZERO : terminal && taskFact != null
                && Objects.equals(taskFact.executionId(), executionFact.executionId()) && taskFact.ledgerTokens() != null
                ? tokens(taskFact.ledgerTokens()) : null;
        if (!SETTLED.equals(assignment.getSettlementStatus())) {
            if (tokens != null) {
                assignment.setSettlementStatus(SETTLED); assignment.setConsumedTokens(tokens); assignment.setSettledAt(now);
                assignment.setUnresolvedSince(null); assignment.setReasonCode(null);
                experiment.setReservedTokenBudget(Math.subtractExact(experiment.getReservedTokenBudget(), assignment.getReservedTokenBudget()));
                experiment.setConsumedTokens(experiment.getConsumedTokens().add(tokens));
                event(experiment, "BUDGET_SETTLED", null, assignment.getId(), null);
                if (experiment.getConsumedTokens().compareTo(BigInteger.valueOf(experiment.getAuthorizedTokenBudget())) > 0) {
                    change(experiment, STOPPING.name(), ONLINE_TOKEN_OVERRUN.name(), null, true, now);
                }
            } else if (terminal || taskFact == null || executionFact == null || executionFact.executionStatus() == null || UNKNOWN.equals(executionFact.executionStatus())) {
                if (assignment.getUnresolvedSince() == null) { assignment.setUnresolvedSince(now); }
                assignment.setSettlementStatus(UNKNOWN); assignment.setReasonCode(ONLINE_FACT_UNKNOWN.name());
                if (ACTIVE.name().equals(experiment.getStatus()) && !now.isBefore(assignment.getUnresolvedSince().plusSeconds(UNRESOLVED_SECONDS))) {
                    change(experiment, PAUSED.name(), ONLINE_FACT_UNKNOWN.name(), null, false, now);
                }
            } else {
                assignment.setSettlementStatus(RESERVED); assignment.setUnresolvedSince(null); assignment.setReasonCode(null);
            }
        } else if (tokens != null && !tokens.equals(assignment.getConsumedTokens())) {
            throw conflict(ONLINE_SLOT_INVALID);
        }
        assignments.updateById(assignment);
        if (!UNKNOWN.equals(assignment.getSettlementStatus())) {
            assignments.update(null, new LambdaUpdateWrapper<OnlineAssignmentEntity>().eq(OnlineAssignmentEntity::getId, assignment.getId())
                    .set(OnlineAssignmentEntity::getUnresolvedSince, null).set(OnlineAssignmentEntity::getReasonCode, null));
        }
        health(experiment, assignment.getVariant(), now);
        experiment.setUnknownTaskCount(Math.toIntExact(assignments.selectCount(new LambdaQueryWrapper<OnlineAssignmentEntity>()
                .eq(OnlineAssignmentEntity::getExperimentId, experimentId).eq(OnlineAssignmentEntity::getSettlementStatus, UNKNOWN))));
        finishIfSettled(experiment, now);
        experiment.setLastReconciledAt(now); experiments.updateById(experiment);
    }

    private void finishIfSettled(OnlineExperimentEntity experiment, LocalDateTime now) {
        if (STOPPING.name().equals(experiment.getStatus()) && experiment.getReservedTokenBudget() == 0
                && experiment.getBaselineSlotCount() == 0 && experiment.getCandidateSlotCount() == 0
                && assignments.selectCount(new LambdaQueryWrapper<OnlineAssignmentEntity>().eq(OnlineAssignmentEntity::getExperimentId, experiment.getId())
                        .ne(OnlineAssignmentEntity::getSettlementStatus, SETTLED)) == 0) {
            experiment.setStatus(STOPPED.name()); experiment.setStoppedAt(now); experiment.setStateVersion(Math.incrementExact(experiment.getStateVersion()));
            experiment.setActiveSlot(null);
            experiments.update(null, new LambdaUpdateWrapper<OnlineExperimentEntity>().eq(OnlineExperimentEntity::getId, experiment.getId())
                    .set(OnlineExperimentEntity::getActiveSlot, null));
            event(experiment, "EXPERIMENT_STOPPED", null, null, experiment.getReasonCode());
        }
    }

    /** 恢复前在同一实验锁内复核未决、原额度和完整健康窗口，不扩大历史授权。 */
    void requireResumeSafe(OnlineExperimentEntity experiment) {
        if (assignments.selectCount(new LambdaQueryWrapper<OnlineAssignmentEntity>()
                .eq(OnlineAssignmentEntity::getExperimentId, experiment.getId())
                .eq(OnlineAssignmentEntity::getSettlementStatus, UNKNOWN)) > 0) { throw conflict(ONLINE_FACT_UNKNOWN); }
        if (experiment.getAssignedTaskCount() >= experiment.getMaxTaskCount()
                || experiment.getConsumedTokens().add(BigInteger.valueOf(experiment.getReservedTokenBudget()))
                .compareTo(BigInteger.valueOf(experiment.getAuthorizedTokenBudget())) >= 0) { throw conflict(ONLINE_BUDGET_EXHAUSTED); }
        if (unhealthy(experiment, "BASELINE") || unhealthy(experiment, "CANDIDATE")) { throw conflict(ONLINE_HEALTH_FAILURE); }
        var units = assignments.runtimeUnits(experiment.getId());
        int candidate = (int) units.stream().filter("CANDIDATE"::equals).count();
        if ("DETECTED".equals(OnlineExperimentStatistics.srm(units.size(), candidate,
                manifest(experiment).path("bucketProtocol").path("weight").asInt()).status())) { throw conflict(SRM_DETECTED); }
    }

    private static void requireLiveControl(OnlineExperimentEntity experiment) {
        if (experiment.getControlAuthorizedBy() == null || experiment.getControlExpiresAt() == null
                || !LocalDateTime.now(ZoneOffset.UTC).isBefore(experiment.getControlExpiresAt())) { throw conflict(CANCEL_AUTHORIZATION_UNAVAILABLE); }
    }

    private boolean unhealthy(OnlineExperimentEntity experiment, String variant) {
        var recent = assignments.selectList(new LambdaQueryWrapper<OnlineAssignmentEntity>()
                .eq(OnlineAssignmentEntity::getExperimentId, experiment.getId()).eq(OnlineAssignmentEntity::getVariant, variant)
                .in(OnlineAssignmentEntity::getExecutionStatus, HEALTH_TERMINAL).isNotNull(OnlineAssignmentEntity::getExecutionTerminalAt)
                .orderByDesc(OnlineAssignmentEntity::getExecutionTerminalAt).orderByDesc(OnlineAssignmentEntity::getId)
                .last("LIMIT " + HEALTH_WINDOW_COUNT));
        long failures = recent.stream().filter(item -> !"COMPLETED".equals(item.getExecutionStatus())).count();
        return recent.size() == HEALTH_WINDOW_COUNT && failures * OnlineProtocolUtils.BUCKET_COUNT >= (long) HEALTH_WINDOW_COUNT * HEALTH_FAILURE_BPS;
    }

    private void health(OnlineExperimentEntity experiment, String variant, LocalDateTime now) {
        if (ACTIVE.name().equals(experiment.getStatus()) && unhealthy(experiment, variant)) {
            change(experiment, PAUSED.name(), ONLINE_HEALTH_FAILURE.name(), null, false, now);
        }
    }

    private void change(OnlineExperimentEntity experiment, String status, String reason, Long actor, boolean emergency, LocalDateTime now) {
        experiment.setStatus(status); experiment.setStateVersion(Math.incrementExact(experiment.getStateVersion()));
        experiment.setReasonCode(reason); experiment.setStateChangedBy(actor); experiment.setStateChangedAt(now);
        if (STOPPING.name().equals(status) && experiment.getStopRequestedAt() == null) { experiment.setStopRequestedAt(now); }
        if (emergency && experiment.getEmergencyStopRequestedAt() == null) { experiment.setEmergencyStopRequestedAt(now); }
        experiments.updateById(experiment);
        if (emergency) { assignments.update(null, new LambdaUpdateWrapper<OnlineAssignmentEntity>()
                .eq(OnlineAssignmentEntity::getExperimentId, experiment.getId()).ne(OnlineAssignmentEntity::getSettlementStatus, SETTLED)
                .set(OnlineAssignmentEntity::getCancelStatus, REQUESTED)); }
        event(experiment, emergency ? "EMERGENCY_STOP_REQUESTED" : "GATE_CLOSED", actor, null, reason);
    }

    private OnlineExecutionSlotEntity slot(OnlineExperimentEntity experiment, String variant) {
        var slot = slots.lock(experiment.getId(), variant);
        if (slot == null) {
            slot = new OnlineExecutionSlotEntity(); slot.setId(IdWorker.getId()); slot.setExperimentId(experiment.getId());
            slot.setVariant(variant); slot.setSlotNo(1); slot.setGeneration(0L); slots.insert(slot);
        }
        return slot;
    }

    private OnlineSlotPermitVO permit(OnlineExecutionSlotEntity slot, OnlineAssignmentEntity assignment) {
        String hash = OnlineProtocolUtils.hash("online.slot-permit", Map.of("experimentId", assignment.getExperimentId().toString(),
                "assignmentId", assignment.getId().toString(), "taskId", assignment.getTaskId().toString(),
                "bindingHash", assignment.getBindingHash(), "generation", Long.toString(slot.getGeneration())));
        return new OnlineSlotPermitVO(binding(assignment), assignment.getBindingHash(), slot.getGeneration(), hash, slot.getStartedAt() != null);
    }

    OnlineTaskBindingDTO binding(OnlineAssignmentEntity value) {
        var envelope = OnlineProtocolUtils.object(value.getBindingJson());
        var binding = JsonUtils.parseStrict(JsonUtils.toJson(envelope.get("payload")), OnlineTaskBindingDTO.class);
        if (binding == null || !OnlineProtocolUtils.canonical("online.binding", binding).equals(value.getBindingJson())
                || !OnlineProtocolUtils.hash("online.binding", binding).equals(value.getBindingHash())
                || !binding.taskId().equals(value.getTaskId().toString()) || !binding.assignmentId().equals(value.getId().toString())
                || !binding.experimentId().equals(value.getExperimentId().toString())
                || !binding.spaceId().equals(value.getSpaceId().toString()) || !binding.agentId().equals(value.getAgentId().toString())
                || !binding.documentId().equals(value.getDocumentId().toString()) || !binding.actorId().equals(value.getCreatedBy().toString())
                || !binding.acceptedSequence().equals(value.getAcceptedSequence().toString())
                || !Objects.equals(binding.variant(), value.getVariant()) || !Objects.equals(binding.bucket(), value.getBucket())
                || !Objects.equals(binding.inputSchemaVersion(), value.getInputSnapshotSchemaVersion())
                || !Objects.equals(binding.inputHash(), value.getInputSnapshotHash())
                || !binding.templateId().equals(value.getTemplateId().toString())
                || !Objects.equals(binding.templateSchemaVersion(), value.getConfigSchemaVersion())
                || !Objects.equals(binding.templateHash(), value.getConfigHash()) || !Objects.equals(binding.dependencyHash(), value.getDependencyHash())
                || !binding.tokenBudget().equals(value.getReservedTokenBudget().toString())
                || binding.schemaVersion() != OnlineProtocolUtils.SCHEMA_VERSION || !Objects.equals(value.getBindingSchemaVersion(), OnlineProtocolUtils.SCHEMA_VERSION)
                || !LIVE.equals(binding.executionMode()) || !ORIGINAL.equals(binding.lineageType())) { throw conflict(MANIFEST_INVALID); }
        return binding;
    }

    JsonNode manifest(OnlineExperimentEntity experiment) {
        var envelope = OnlineProtocolUtils.object(experiment.getManifestJson());
        if (!Objects.equals(experiment.getManifestSchemaVersion(), OnlineProtocolUtils.SCHEMA_VERSION)
                || !experiment.getManifestHash().equals(StableSnapshotUtils.sha256Utf8(experiment.getManifestJson()))
                || !OnlineProtocolUtils.canonicalNode(envelope).equals(experiment.getManifestJson())
                || !"online.manifest".equals(envelope.path("domain").asText())
                || envelope.path("schemaVersion").asInt() != OnlineProtocolUtils.SCHEMA_VERSION
                || !experiment.getId().toString().equals(envelope.path("payload").path("experimentId").asText())
                || !experiment.getSpaceId().toString().equals(envelope.path("payload").path("spaceId").asText())
                || !experiment.getAgentId().toString().equals(envelope.path("payload").path("agentId").asText())) { throw conflict(MANIFEST_INVALID); }
        return envelope.get("payload");
    }

    private OnlineExperimentEntity lock(Long id) {
        var experiment = experiments.lock(id);
        if (experiment == null) { throw conflict(ONLINE_EXPERIMENT_NOT_FOUND); }
        manifest(experiment);
        return experiment;
    }

    private OnlineAssignmentEntity requireAssignment(Long experimentId, Long taskId) {
        var value = assignments.selectOne(new LambdaQueryWrapper<OnlineAssignmentEntity>().eq(OnlineAssignmentEntity::getTaskId, taskId));
        if (value == null || !experimentId.equals(value.getExperimentId())) { throw conflict(ONLINE_SLOT_INVALID); }
        return value;
    }

    private static OnlineAssignmentRequestDTO requestOf(OnlineTaskBindingDTO binding, OnlineAssignmentEntity assignment) {
        return new OnlineAssignmentRequestDTO(binding.taskId(), binding.spaceId(), binding.agentId(), binding.documentId(), binding.actorId(),
                assignment.getClientRequestKey(), assignment.getRequestHash(), binding.inputHash(), binding.documentVersion(),
                binding.documentContentHash(), binding.tokenBudget(), binding.inputSchemaVersion());
    }

    private static boolean containsDocument(JsonNode manifest, String id) {
        for (var value : manifest.path("documentIds")) { if (id.equals(value.asText())) { return true; } }
        return false;
    }

    private static void validate(OnlineAssignmentRequestDTO request) {
        if (request == null || request.requestKey() == null || !request.requestKey().matches("[A-Za-z0-9._:-]{1,64}")
                || request.inputSchemaVersion() != INPUT_SCHEMA_VERSION) { throw conflict(MANIFEST_INVALID); }
        try {
            for (String value : new String[]{request.taskId(), request.spaceId(), request.agentId(), request.documentId(), request.actorId(),
                    request.documentVersion(), request.tokenBudget()}) { OnlineProtocolUtils.id(value); }
        } catch (IllegalArgumentException invalid) { throw conflict(MANIFEST_INVALID); }
        for (String value : new String[]{request.requestHash(), request.inputHash(), request.documentContentHash()}) {
            if (value == null || !value.matches("[0-9a-f]{64}")) { throw conflict(MANIFEST_INVALID); }
        }
    }

    private static void verifyFact(OnlineAssignmentEntity assignment, OnlineTaskFactVO fact) {
        if (fact != null && (!assignment.getTaskId().toString().equals(fact.taskId()) || !assignment.getBindingHash().equals(fact.bindingHash()))) {
            throw conflict(ONLINE_SLOT_INVALID);
        }
    }

    private static BigInteger tokens(String value) {
        if (!value.matches("0|[1-9][0-9]{0,37}")) { throw conflict(MANIFEST_INVALID); }
        return new BigInteger(value);
    }

    private static void countSlots(OnlineExperimentEntity experiment, String variant, int delta) {
        if ("BASELINE".equals(variant)) { experiment.setBaselineSlotCount(Math.addExact(experiment.getBaselineSlotCount(), delta)); }
        else { experiment.setCandidateSlotCount(Math.addExact(experiment.getCandidateSlotCount(), delta)); }
    }

    private void event(OnlineExperimentEntity experiment, String type, Long actor, Long assignmentId, String reason) {
        var event = new OnlineExperimentEventEntity(); event.setId(IdWorker.getId()); event.setExperimentId(experiment.getId());
        event.setSpaceId(experiment.getSpaceId()); event.setEventType(type); event.setActorType(actor == null ? "SERVICE" : "HUMAN");
        event.setActorId(actor == null ? "evaluation-service" : actor.toString());
        event.setAuthorizedBy(actor == null ? experiment.getControlAuthorizedBy() : actor); event.setStateVersion(experiment.getStateVersion());
        event.setAssignmentId(assignmentId); event.setReasonCode(reason); events.insert(event);
    }

    private static BusinessException conflict(OnlineReasonCode reason) { return new BusinessException(ErrorCode.CONFLICT, reason.name()); }

    /** 本事务提交之后才可将 reason 转为 HTTP 错误。 */
    public record Allocation(OnlineTaskBindingDTO binding, String reason) { }
}
