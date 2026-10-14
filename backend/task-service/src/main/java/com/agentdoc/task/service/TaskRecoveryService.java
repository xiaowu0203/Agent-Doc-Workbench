package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.RedisKeyConstants;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.TaskRecoveryAuthFeign;
import com.agentdoc.common.feign.TaskRecoveryDocumentFeign;
import com.agentdoc.common.feign.dto.TaskRecoveryIdentityDTO;
import com.agentdoc.common.feign.dto.TaskRecoveryIssueDTO;
import com.agentdoc.common.feign.vo.TaskRecoveryRemoteVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.a2a.A2aTaskSynchronizationService;
import com.agentdoc.task.a2a.TaskRecoveryClient;
import com.agentdoc.task.config.TaskRecoveryProperties;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.convertor.A2aTaskConvertor;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.TaskRecoveryReason;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.vo.TaskRecoveryEventVO;
import com.agentdoc.task.pojo.vo.TaskRecoveryStatusVO;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.a2aproject.sdk.spec.Task;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_EDIT;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_TERMINATE;

/** 自动/人工共用恢复流程，不新增 Task 状态，不持久化或透传恢复 JWT。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskRecoveryService {
    private final TaskMapper taskMapper;
    private final TaskCapabilityCryptoService cryptoService;
    private final TaskRecoveryProperties properties;
    private final TaskRecoveryAuthFeign authFeign;
    private final TaskRecoveryDocumentFeign recoveryDocumentFeign;
    private final TaskRecoveryClient recoveryClient;
    private final A2aTaskSynchronizationService synchronizationService;
    private final AuditLogService auditService;
    private final DocumentFeign documentFeign;
    private final RedisUtils redisUtils;

    /** 只读诊断：即使无恢复配置或原凭证损坏也能查看脱敏原因。 */
    public TaskRecoveryStatusVO status(Long taskId) {
        AuthUtils.getUserIdOrException();
        TaskEntity task = requireTask(taskId);
        requirePermission(task.getSpaceId(), TASK_READ);
        return status(task);
    }

    /** 人工只触发已有流程；不接受状态、凭证、远端 ID 或新取消意图。 */
    public TaskRecoveryStatusVO recoverManually(Long taskId) {
        Long userId = AuthUtils.getUserIdOrException();
        TaskEntity task = requireTask(taskId);
        requirePermission(task.getSpaceId(), TASK_READ);
        requirePermission(task.getSpaceId(), TASK_TERMINATE);
        if (needsDraft(task)) { requirePermission(task.getSpaceId(), DOCUMENT_EDIT); }
        String key = RedisKeyConstants.TASK_A2A_RECONCILE_LOCK_PREFIX + taskId;
        String owner = UUID.randomUUID().toString();
        long started = System.nanoTime();
        if (!redisUtils.setIfAbsent(key, owner, Duration.ofSeconds(TaskConstant.A2A_RECONCILE_LOCK_SECONDS))) {
            throw failure(TaskRecoveryReason.RECOVERY_CAPACITY_EXCEEDED);
        }
        try {
            TaskEntity current = requireTask(taskId);
            if (!Objects.equals(task.getSpaceId(), current.getSpaceId())) {
                throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
            }
            recoverLocked(current, () -> ownsLock(key, owner, started), userId);
            return status(requireTask(taskId));
        } finally {
            if (!redisUtils.deleteIfValueMatches(key, owner)) {
                log.debug("Task 恢复结束时锁已被接管，taskId={}", taskId);
            }
        }
    }

    /** 本地 expiry 只用于选择签发路径；auth-service 必须先验签，才能申请任何远端权限。 */
    public boolean hasExpiredProof(TaskEntity task) {
        return expired(cryptoService.decrypt(task.getCapabilityToken()));
    }

    /** 批次已超出处理容量时明确告警，不承诺健康容量之外的五分钟 SLA。 */
    public void reportCapacity(TaskEntity task) {
        String recoveryId = UUID.randomUUID().toString();
        if (redisUtils.setIfAbsent(TaskRecoveryConstant.ALERT_KEY_PREFIX + "capacity", recoveryId,
                Duration.ofSeconds(TaskRecoveryConstant.ALERT_WINDOW_SECONDS))) {
            append(task, recoveryId, "AUTO", null, AuditAction.TASK_RECOVERY_ALERT, "ALERT",
                    TaskRecoveryReason.RECOVERY_CAPACITY_EXCEEDED, Instant.now().toString(), Instant.now().toString());
            log.warn("Task 对账积压超过单批容量，reason=RECOVERY_CAPACITY_EXCEEDED，taskId={}", task.getId());
        }
    }

    /** 调用方已经持有同一 Task owner-token 锁，数据库身份在每轮恢复中重新核验。 */
    public TaskRecoveryReason recoverLocked(TaskEntity candidate, BooleanSupplier owner, Long userId) {
        TaskEntity task = requireTask(candidate.getId());
        if (!active(task) || task.getA2aTaskId() == null) { return TaskRecoveryReason.NOT_REQUIRED; }
        String recoveryId = UUID.randomUUID().toString();
        String startedAt = Instant.now().toString();
        String trigger = userId == null ? "AUTO" : "MANUAL";
        TaskRecoveryReason fallback = TaskRecoveryReason.SOURCE_CAPABILITY_INVALID;
        String action = "QUERY";
        TaskRecoveryReason outcome;
        try {
            String source = cryptoService.decrypt(task.getCapabilityToken());
            if (!expired(source)) { return TaskRecoveryReason.NOT_REQUIRED; }
            append(task, recoveryId, trigger, userId, AuditAction.TASK_RECOVERY_STARTED,
                    action, TaskRecoveryReason.CAPABILITY_EXPIRED, startedAt, null);
            if (!properties.isConfigured()) { throw failure(TaskRecoveryReason.RECOVERY_SERVICE_UNCONFIGURED); }
            requireOwner(owner);
            TaskRecoveryIdentityDTO identity = identity(task);
            boolean cancel = TaskStatus.CANCELING.getCodeEquals(task.getStatus());
            TaskRecoveryIssueDTO query = new TaskRecoveryIssueDTO(source, identity, task.getA2aTaskId(), recoveryId, cancel, null);
            fallback = TaskRecoveryReason.RECOVERY_ISSUANCE_FAILED;
            String capability = issued(authFeign.issueRecovery(properties.getMachineKey(), query));
            requireOwner(owner);
            fallback = TaskRecoveryReason.REMOTE_TASK_UNAVAILABLE;
            TaskRecoveryRemoteVO<Task> remote = recoveryClient.query(task.getA2aTaskId(), capability, false);
            requireRemote(task, remote);
            requireOwner(owner);
            if (cancel && !remote.remoteTask().status().state().isFinal()) {
                action = "CANCEL";
                append(task, recoveryId, trigger, userId, AuditAction.TASK_RECOVERY_ACTION,
                        action, TaskRecoveryReason.CAPABILITY_EXPIRED, startedAt, null);
                remote = recoveryClient.query(task.getA2aTaskId(), capability, true);
                requireRemote(task, remote);
                requireOwner(owner);
            }
            TaskStatus terminal = A2aTaskConvertor.mapStatus(remote.remoteTask().status().state());
            if (!remote.remoteTask().status().state().isFinal()) {
                synchronizationService.synchronizeRecovered(task, remote.remoteTask(), remote.tokenUsage(), owner);
                outcome = TaskRecoveryReason.REMOTE_TASK_ACTIVE;
            } else {
                // 先验证同一执行/完整账本，避免草稿已提交后才发现无法完成本地写回。
                requireUsage(task, remote);
                TaskEntity current = requireTask(task.getId());
                if (!active(current)) { throw failure(TaskRecoveryReason.NOT_REQUIRED); }
                if (!identity(current).equals(identity) || !Objects.equals(current.getA2aTaskId(), task.getA2aTaskId())) {
                    throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
                }
                requireOwner(owner);
                if (needsDraft(task)) {
                    action = terminal == TaskStatus.COMPLETED ? "FINALIZE" : "DISCARD";
                    fallback = TaskRecoveryReason.RECOVERY_ISSUANCE_FAILED;
                    String remoteStatus = terminal == TaskStatus.COMPLETED ? "COMPLETED"
                            : terminal == TaskStatus.TERMINATED ? "CANCELED" : "FAILED";
                    String draftCapability = issued(authFeign.issueFinalization(properties.getMachineKey(),
                            new TaskRecoveryIssueDTO(source, identity, task.getA2aTaskId(), recoveryId, cancel, remoteStatus)));
                    requireOwner(owner);
                    append(task, recoveryId, trigger, userId, AuditAction.TASK_RECOVERY_ACTION,
                            action, TaskRecoveryReason.CAPABILITY_EXPIRED, startedAt, null);
                    fallback = TaskRecoveryReason.RECOVERY_WRITEBACK_FAILED;
                    Result<Void> draft = recoveryDocumentFeign.finalizeDraft(task.getId(), draftCapability);
                    if (draft == null || draft.code() != ErrorCode.SUCCESS.getCode()) {
                        throw failure(draft != null && "DRAFT_VERSION_CONFLICT".equals(draft.message())
                                ? TaskRecoveryReason.DRAFT_VERSION_CONFLICT : TaskRecoveryReason.RECOVERY_WRITEBACK_FAILED);
                    }
                    requireOwner(owner);
                }
                action = "WRITEBACK";
                fallback = TaskRecoveryReason.RECOVERY_WRITEBACK_FAILED;
                boolean updated = synchronizationService.synchronizeRecovered(task, remote.remoteTask(), remote.tokenUsage(), owner);
                outcome = updated ? TaskRecoveryReason.RECOVERED : TaskRecoveryReason.NOT_REQUIRED;
            }
        } catch (RuntimeException exception) {
            outcome = reason(exception, fallback);
        }
        append(task, recoveryId, trigger, userId, AuditAction.TASK_RECOVERY_FINISHED,
                action, outcome, startedAt, Instant.now().toString());
        if (outcome != TaskRecoveryReason.RECOVERED && outcome != TaskRecoveryReason.NOT_REQUIRED
                && outcome != TaskRecoveryReason.REMOTE_TASK_ACTIVE) {
            alert(task, recoveryId, trigger, userId, outcome, startedAt);
        }
        return outcome;
    }

    private void alert(TaskEntity task, String recoveryId, String trigger, Long userId,
                       TaskRecoveryReason reason, String startedAt) {
        List<TaskRecoveryEventVO> recent = auditService.recentRecoveryResults(task.getId());
        boolean changed = recent.size() > 1 && recent.get(1).reason() != reason;
        boolean threshold = recent.size() == TaskRecoveryConstant.FAILURE_ALERT_THRESHOLD
                && recent.stream().allMatch(event -> event.reason() == reason);
        String key = TaskRecoveryConstant.ALERT_KEY_PREFIX + task.getId() + ":" + reason.name();
        Duration window = Duration.ofSeconds(TaskRecoveryConstant.ALERT_WINDOW_SECONDS);
        boolean firstOrHourly = redisUtils.setIfAbsent(key + ":first", recoveryId, window);
        boolean third = threshold && redisUtils.setIfAbsent(key + ":threshold", recoveryId, window);
        if (firstOrHourly || changed || third) {
            append(task, recoveryId, trigger, userId, AuditAction.TASK_RECOVERY_ALERT,
                    "ALERT", reason, startedAt, Instant.now().toString());
            log.warn("Task 终态恢复需要关注，taskId={}，reason={}，recoveryId={}", task.getId(), reason, recoveryId);
        }
    }

    private void append(TaskEntity task, String recoveryId, String trigger, Long userId, AuditAction auditAction,
                        String action, TaskRecoveryReason reason, String start, String finish) {
        auditService.recordRecovery(task.getSpaceId(), task.getId(), auditAction,
                new TaskRecoveryEventVO(recoveryId, trigger, TaskRecoveryConstant.SERVICE,
                        userId, task.getA2aTaskId(), action, reason, start, finish));
    }

    private TaskRecoveryStatusVO status(TaskEntity task) {
        TaskRecoveryReason reason = TaskRecoveryReason.NOT_REQUIRED;
        boolean required = false;
        if (active(task) && task.getA2aTaskId() != null) {
            try { required = hasExpiredProof(task); }
            catch (RuntimeException exception) { required = true; reason = TaskRecoveryReason.SOURCE_CAPABILITY_INVALID; }
        }
        TaskRecoveryEventVO event = auditService.latestRecoveryEvent(task.getId());
        if (required && reason != TaskRecoveryReason.SOURCE_CAPABILITY_INVALID) { reason = !properties.isConfigured() ? TaskRecoveryReason.RECOVERY_SERVICE_UNCONFIGURED
                : event == null ? TaskRecoveryReason.CAPABILITY_EXPIRED : event.reason(); }
        return new TaskRecoveryStatusVO(task.getId(), task.getStatus(), properties.isConfigured(), required, reason, event);
    }

    private void requireRemote(TaskEntity task, TaskRecoveryRemoteVO<Task> result) {
        Task remote = result == null ? null : result.remoteTask();
        if (remote == null || !Objects.equals(remote.id(), task.getA2aTaskId())
                || !Objects.equals(remote.contextId(), task.getA2aContextId())
                || remote.status() == null || remote.status().state() == null) {
            throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
        }
    }

    private void requireUsage(TaskEntity task, TaskRecoveryRemoteVO<Task> remote) {
        var usage = remote.tokenUsage();
        if (usage == null || usage.executionId() == null || usage.modelId() == null || usage.modelConfigVersion() == null
                || usage.inputPricePerMillion() == null || usage.outputPricePerMillion() == null || usage.currency() == null
                || usage.pricingSchemaVersion() == null || usage.pricingCapturedAt() == null) {
            throw failure(TaskRecoveryReason.RECOVERY_WRITEBACK_FAILED);
        }
        if (task.getAgentExecutionId() != null && !Objects.equals(task.getAgentExecutionId(), usage.executionId())) {
            throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
        }
        TaskEntity projected = new TaskEntity();
        BeanUtils.copyProperties(task, projected);
        A2aTaskConvertor.apply(projected, remote.remoteTask());
        if (projected.getAgentExecutionId() != null && !Objects.equals(projected.getAgentExecutionId(), usage.executionId())) {
            throw failure(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
        }
    }

    private String issued(Result<String> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null || result.data().isBlank()) {
            throw failure(result != null && "SOURCE_CAPABILITY_INVALID".equals(result.message())
                    ? TaskRecoveryReason.SOURCE_CAPABILITY_INVALID : TaskRecoveryReason.RECOVERY_ISSUANCE_FAILED);
        }
        return result.data();
    }

    private boolean expired(String capability) {
        if (capability == null || capability.length() > TaskRecoveryConstant.MAX_PROOF_LENGTH) { throw failure(TaskRecoveryReason.SOURCE_CAPABILITY_INVALID); }
        String[] parts = capability.split("\\.");
        if (parts.length != 3) { throw failure(TaskRecoveryReason.SOURCE_CAPABILITY_INVALID); }
        JsonNode payload = JsonUtils.parse(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8), JsonNode.class);
        if (payload == null || !payload.path("exp").isIntegralNumber()) { throw failure(TaskRecoveryReason.SOURCE_CAPABILITY_INVALID); }
        return payload.path("exp").asLong() <= Instant.now().getEpochSecond();
    }

    private TaskRecoveryIdentityDTO identity(TaskEntity task) {
        return new TaskRecoveryIdentityDTO(task.getId(), task.getAgentId(), task.getSpaceId(), task.getDocumentId(),
                task.getExecutionMode(), task.getDocumentVersionSnapshot(), task.getDocumentContentSha256(),
                task.getInputSnapshotSchemaVersion(), task.getInputSnapshotHash(), task.getDerivationRequestHash(), TaskOnlineDispatchService.identity(task));
    }

    private boolean needsDraft(TaskEntity task) {
        return TaskExecutionMode.LIVE.name().equals(task.getExecutionMode())
                && Objects.equals(task.getDocumentType(), DocType.DRAFT.getCode());
    }

    private boolean active(TaskEntity task) { return TaskStatus.remoteActiveCodes().contains(task.getStatus()); }

    private TaskEntity requireTask(Long id) {
        TaskEntity task = taskMapper.selectById(id);
        if (task == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在"); }
        return task;
    }

    private void requirePermission(Long spaceId, String permission) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, permission);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "任务恢复权限不足");
        }
    }

    private boolean ownsLock(String key, String owner, long started) {
        // Redis 响应也可能等待：必须在拿到 owner 后检查剩余预算，不能使用调用前的时间判断。
        return owner.equals(redisUtils.get(key))
                && System.nanoTime() - started < Duration.ofSeconds(TaskRecoveryConstant.PROCESS_BUDGET_SECONDS).toNanos();
    }

    private void requireOwner(BooleanSupplier owner) {
        if (!owner.getAsBoolean()) { throw failure(TaskRecoveryReason.RECOVERY_CAPACITY_EXCEEDED); }
    }

    private TaskRecoveryReason reason(RuntimeException exception, TaskRecoveryReason fallback) {
        if (exception instanceof BusinessException) {
            try { return TaskRecoveryReason.valueOf(exception.getMessage()); }
            catch (RuntimeException ignored) { return fallback; }
        }
        return fallback;
    }

    private BusinessException failure(TaskRecoveryReason reason) {
        return new BusinessException(ErrorCode.CONFLICT, reason.name());
    }
}
