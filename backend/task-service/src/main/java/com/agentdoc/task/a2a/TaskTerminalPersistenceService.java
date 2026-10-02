package com.agentdoc.task.a2a;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.service.ExecutionArtifactService;
import com.agentdoc.task.service.TokenUsageService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.BooleanSupplier;

/** 只包含本地 Task/账本/产物写入的短事务；不在事务内做 HTTP 调用。 */
@Service
@RequiredArgsConstructor
public class TaskTerminalPersistenceService {
    private final TaskMapper taskMapper;
    private final TokenUsageService tokenUsageService;
    private final ExecutionArtifactService executionArtifactService;

    @Transactional(rollbackFor = Exception.class)
    public boolean persist(TaskEntity task, A2aTokenUsage usage, BooleanSupplier ownsLock) {
        requireOwner(ownsLock);
        LambdaUpdateWrapper<TaskEntity> update = new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, task.getId())
                .in(TaskEntity::getStatus, TaskStatus.remoteActiveCodes())
                .and(binding -> binding.isNull(TaskEntity::getA2aTaskId)
                        .or().eq(TaskEntity::getA2aTaskId, task.getA2aTaskId()))
                .and(binding -> binding.isNull(TaskEntity::getA2aContextId)
                        .or().eq(TaskEntity::getA2aContextId, task.getA2aContextId()))
                .eq(task.getSpaceId() != null, TaskEntity::getSpaceId, task.getSpaceId())
                .eq(task.getAgentId() != null, TaskEntity::getAgentId, task.getAgentId())
                .eq(task.getDocumentId() != null, TaskEntity::getDocumentId, task.getDocumentId())
                .eq(task.getExecutionMode() != null, TaskEntity::getExecutionMode, task.getExecutionMode())
                .eq(task.getDocumentVersionSnapshot() != null, TaskEntity::getDocumentVersionSnapshot, task.getDocumentVersionSnapshot())
                .eq(task.getDocumentContentSha256() != null, TaskEntity::getDocumentContentSha256, task.getDocumentContentSha256())
                .eq(task.getInputSnapshotSchemaVersion() != null, TaskEntity::getInputSnapshotSchemaVersion, task.getInputSnapshotSchemaVersion())
                .eq(task.getInputSnapshotHash() != null, TaskEntity::getInputSnapshotHash, task.getInputSnapshotHash())
                .and(binding -> {
                    binding.isNull(TaskEntity::getAgentExecutionId);
                    if (task.getAgentExecutionId() != null) { binding.or().eq(TaskEntity::getAgentExecutionId, task.getAgentExecutionId()); }
                })
                .set(TaskEntity::getA2aContextId, task.getA2aContextId())
                .set(TaskEntity::getA2aTaskId, task.getA2aTaskId())
                .set(TaskEntity::getStatus, task.getStatus())
                .set(TaskEntity::getLastHeartbeatAt, task.getLastHeartbeatAt())
                .set(TaskEntity::getEndTime, task.getEndTime())
                .set(TaskEntity::getResultSummary, task.getResultSummary())
                .set(TaskEntity::getErrorMessage, task.getErrorMessage())
                .set(TaskEntity::getTokensUsed, task.getTokensUsed())
                .set(TaskEntity::getTokensEstimated, task.getTokensEstimated())
                .set(TaskEntity::getAgentExecutionId, task.getAgentExecutionId())
                .set(TaskEntity::getPromptHash, task.getPromptHash());
        if (task.getDerivationRequestHash() == null) { update.isNull(TaskEntity::getDerivationRequestHash); }
        else { update.eq(TaskEntity::getDerivationRequestHash, task.getDerivationRequestHash()); }
        if (TaskStatus.remoteActiveCodes().contains(task.getStatus())
                && !TaskStatus.CANCELING.getCodeEquals(task.getStatus())) {
            // 远端查询期间新记录的取消意图不能被旧活跃投影覆盖。
            update.ne(TaskEntity::getStatus, TaskStatus.CANCELING.getCode());
        }
        if (taskMapper.update(null, update) == 0) { return false; }
        if (usage != null) {
            tokenUsageService.recordRemote(task, usage);
            if (TaskStatus.COMPLETED.getCodeEquals(task.getStatus())
                    && TaskExecutionMode.ISOLATED.name().equals(task.getExecutionMode())) {
                executionArtifactService.appendResultSummary(task);
            }
        }
        requireOwner(ownsLock);
        return true;
    }

    private void requireOwner(BooleanSupplier ownsLock) {
        if (!ownsLock.getAsBoolean()) {
            throw new BusinessException(ErrorCode.CONFLICT, "RECOVERY_CAPACITY_EXCEEDED");
        }
    }
}
