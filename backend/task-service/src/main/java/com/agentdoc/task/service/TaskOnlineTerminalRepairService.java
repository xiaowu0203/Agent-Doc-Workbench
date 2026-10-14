package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.RedisKeyConstants;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineAgentFeign;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.dto.OnlineReportFactsQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import com.agentdoc.common.feign.vo.OnlineReportUsageVO;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.utils.OnlineIdentityUtils;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.a2a.A2aTokenUsage;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.convertor.A2aTaskConvertor;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.agentdoc.task.a2a.TaskTerminalPersistenceService;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.TaskRecoveryReason;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.vo.TaskRecoveryEventVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 人工核验线上迟到终态；复用已有只读来源，无暂存才允许本地条件写回。 */
@Service
@RequiredArgsConstructor
public class TaskOnlineTerminalRepairService {
    private final TaskMapper tasks;
    private final OnlineEvaluationFeign evaluation;
    private final AgentFeign ledger;
    private final OnlineAgentFeign agents;
    private final OnlineDocumentFeign documents;
    private final TaskCapabilityVerifier verifier;
    private final TaskCapabilityCryptoService crypto;
    private final A2aTaskClient a2a;
    private final TaskTerminalPersistenceService persistence;
    private final AuditLogService audit;
    private final RedisUtils redis;

    public boolean pending(Long taskId) { return pending(tasks.selectById(taskId)); }

    /** false 表示不属于此窄路径，仍交给原过期凭证恢复策略。 */
    public boolean repair(Long taskId) {
        long userId = AuthUtils.getUserIdOrException();
        if (!pending(tasks.selectById(taskId))) { return false; }
        String key = RedisKeyConstants.TASK_A2A_RECONCILE_LOCK_PREFIX + taskId;
        String owner = UUID.randomUUID().toString(); long started = System.nanoTime();
        if (!redis.setIfAbsent(key, owner, Duration.ofSeconds(TaskConstant.A2A_RECONCILE_LOCK_SECONDS))) {
            throw failure(TaskRecoveryReason.RECOVERY_CAPACITY_EXCEEDED);
        }
        TaskEntity task = null; String start = Instant.now().toString();
        try {
            task = tasks.selectById(taskId);
            if (!pending(task)) { return false; }
            String experiment = task.getOnlineExperimentId().toString();
            var binding = data(evaluation.humanBinding(taskId.toString()));
            TaskOnlineObservationService.requireIdentity(task, binding, OnlineProtocolUtils.hash("online.binding", binding));
            if (userId != task.getCreatedBy()) { throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH); }
            // 当前人类权限与既有有效任务凭证共同约束；STOPPED 不重新签发 CONTROL。
            var proof = data(evaluation.humanProof(experiment));
            if (!experiment.equals(proof.experimentId()) || !binding.spaceId().equals(proof.spaceId())
                    || !binding.manifestHash().equals(proof.manifestHash()) || !Long.toString(userId).equals(proof.authorizedBy())) {
                throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
            }
            requireSuccess(documents.requireNoDraftStaging(taskId.toString()));
            var rows = data(agents.reportFacts(new OnlineReportFactsQueryDTO(binding.spaceId(), experiment, List.of(binding))));
            if (rows.size() != 1) { throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH); }
            var fact = rows.getFirst();
            if (!"AVAILABLE".equals(fact.state()) || !taskId.toString().equals(fact.taskId())
                    || !binding.assignmentId().equals(fact.assignmentId()) || !task.getOnlineBindingHash().equals(fact.bindingHash())
                    || !String.valueOf(task.getAgentExecutionId()).equals(fact.executionId())) {
                throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
            }
            TaskStatus terminal = switch (Objects.toString(fact.status(), "")) {
                case "COMPLETED" -> TaskStatus.COMPLETED;
                case "FAILED" -> TaskStatus.FAILED;
                case "CANCELED" -> TaskStatus.TERMINATED;
                default -> throw failure(TaskRecoveryReason.REMOTE_TASK_ACTIVE);
            };
            var projection = ledger.getExecutionTokenUsage(taskId);
            AgentExecutionTokenUsageVO usage = projection != null && projection.code() == ErrorCode.SUCCESS.getCode() ? projection.data() : null;
            requireUsage(task, usage);
            if (!Objects.equals(fact.usage(), OnlineReportUsageVO.from(usage)) || fact.finishedAt() == null) {
                throw failure(TaskRecoveryReason.RECOVERY_WRITEBACK_FAILED);
            }
            LocalDateTime finished = LocalDateTime.parse(fact.finishedAt());
            String source = crypto.decrypt(task.getCapabilityToken());
            OnlineIdentityUtils.requireJwt(verifier.verify(source), TaskOnlineDispatchService.identity(task));
            var remote = a2a.getForReconciliation(task.getA2aTaskId(), source);
            if (remote == null || remote.status() == null || remote.status().state() == null
                    || !remote.status().state().isFinal() || !Objects.equals(task.getA2aTaskId(), remote.id())
                    || !Objects.equals(task.getA2aContextId(), remote.contextId())
                    || A2aTaskConvertor.mapStatus(remote.status().state()) != terminal) {
                throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
            }
            requireSuccess(documents.requireNoDraftStaging(taskId.toString()));
            requireLock(key, owner, started);
            var current = tasks.selectById(taskId);
            if (!pending(current)) { return true; }
            TaskOnlineObservationService.requireIdentity(current, binding, OnlineProtocolUtils.hash("online.binding", binding));
            if (!Objects.equals(current.getAgentExecutionId(), task.getAgentExecutionId())
                    || !Objects.equals(current.getA2aTaskId(), remote.id())
                    || !Objects.equals(current.getA2aContextId(), remote.contextId())) {
                throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
            }
            A2aTaskConvertor.apply(current, remote);
            if (!Objects.equals(current.getAgentExecutionId(), usage.executionId())) { throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH); }
            current.setEndTime(finished);
            current.setTokensUsed(Math.addExact(usage.inputTokens(), usage.outputTokens()));
            current.setTokensEstimated(Boolean.TRUE.equals(usage.inputTokensEstimated()) || Boolean.TRUE.equals(usage.outputTokensEstimated()));
            var tokens = new A2aTokenUsage(usage.inputTokens(), usage.cachedInputTokens(), usage.outputTokens(),
                    Boolean.TRUE.equals(usage.inputTokensEstimated()), Boolean.TRUE.equals(usage.cachedInputTokensEstimated()),
                    Boolean.TRUE.equals(usage.outputTokensEstimated()), usage.executionId(), usage.modelId(), usage.modelConfigVersion(),
                    usage.inputPricePerMillion(), usage.outputPricePerMillion(), usage.currency(), usage.pricingSchemaVersion(), usage.pricingCapturedAt());
            boolean changed = persistence.persist(current, tokens, () -> ownsLock(key, owner, started));
            event(task, owner, userId, changed ? TaskRecoveryReason.RECOVERED : TaskRecoveryReason.NOT_REQUIRED, start);
            return true;
        } catch (RuntimeException failure) {
            if (task != null) { event(task, owner, userId, TaskRecoveryReason.RECOVERY_WRITEBACK_FAILED, start); }
            throw failure;
        } finally {
            redis.deleteIfValueMatches(key, owner);
        }
    }

    private static boolean pending(TaskEntity task) {
        return task != null && task.getOnlineAssignmentId() != null && task.getOnlineExperimentId() != null
                && TaskStatus.CANCELING.getCodeEquals(task.getStatus()) && task.getA2aTaskId() != null
                && task.getAgentExecutionId() != null && "LIVE".equals(task.getExecutionMode())
                && Integer.valueOf(DocType.DRAFT.getCode()).equals(task.getDocumentType());
    }
    private static void requireUsage(TaskEntity task, AgentExecutionTokenUsageVO usage) {
        if (usage == null || !Objects.equals(task.getAgentExecutionId(), usage.executionId()) || usage.modelId() == null
                || usage.modelConfigVersion() == null || usage.inputPricePerMillion() == null || usage.outputPricePerMillion() == null
                || usage.currency() == null || usage.pricingSchemaVersion() == null || usage.pricingCapturedAt() == null
                || usage.inputTokens() == null || usage.outputTokens() == null || usage.inputTokens() < 0 || usage.outputTokens() < 0) {
            throw failure(TaskRecoveryReason.RECOVERY_WRITEBACK_FAILED);
        }
    }
    private boolean ownsLock(String key, String owner, long started) {
        return System.nanoTime() - started < Duration.ofSeconds(TaskConstant.A2A_RECONCILE_LOCK_SECONDS).toNanos()
                && owner.equals(redis.get(key));
    }
    private void requireLock(String key, String owner, long started) {
        if (!ownsLock(key, owner, started)) { throw failure(TaskRecoveryReason.RECOVERY_CAPACITY_EXCEEDED); }
    }
    private void event(TaskEntity task, String recoveryId, long userId, TaskRecoveryReason reason, String start) {
        audit.recordRecovery(task.getSpaceId(), task.getId(), AuditAction.TASK_RECOVERY_FINISHED,
                new TaskRecoveryEventVO(recoveryId, "MANUAL", TaskRecoveryConstant.SERVICE, userId, task.getA2aTaskId(), "WRITEBACK", reason, start, Instant.now().toString()));
    }
    private static <T> T data(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw failure(TaskRecoveryReason.RECOVERY_ISSUANCE_FAILED);
        }
        return result.data();
    }
    private static void requireSuccess(Result<?> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.CONFLICT.getCode() : result.code(),
                    result == null ? "RECOVERY_WRITEBACK_FAILED" : result.message());
        }
    }
    private static BusinessException failure(TaskRecoveryReason reason) { return new BusinessException(ErrorCode.CONFLICT, reason.name()); }
}
