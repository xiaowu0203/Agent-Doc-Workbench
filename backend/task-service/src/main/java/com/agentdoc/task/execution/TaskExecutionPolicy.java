package com.agentdoc.task.execution;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.task.enums.TaskExecutionMode;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.pojo.entity.TaskEntity;

import java.util.EnumSet;

/**
 * Phase 1 任务血缘与执行模式统一校验入口。
 */
public final class TaskExecutionPolicy {

    private static final EnumSet<TaskLineageType> PHASE_ONE_LIVE_TYPES = EnumSet.of(
            TaskLineageType.ORIGINAL, TaskLineageType.RERUN, TaskLineageType.REVIEW_REWORK);

    private TaskExecutionPolicy() {
    }

    /**
     * 校验 Phase 1 新建和调度都支持的执行语义。
     *
     * @param task 待创建或调度任务
     */
    public static void requireSupported(TaskEntity task) {
        TaskLineageType lineageType;
        TaskExecutionMode executionMode;
        try {
            lineageType = TaskLineageType.valueOf(task.getLineageType());
            executionMode = TaskExecutionMode.valueOf(task.getExecutionMode());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务执行语义不完整或不合法");
        }
        if (executionMode != TaskExecutionMode.LIVE || !PHASE_ONE_LIVE_TYPES.contains(lineageType)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前阶段不支持该任务执行类型或模式");
        }
    }
}
