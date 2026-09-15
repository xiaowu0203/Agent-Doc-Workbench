package com.agentdoc.task.execution;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.task.enums.TaskExecutionMode;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.pojo.entity.TaskEntity;

import java.util.EnumSet;

/**
 * 任务血缘与执行模式统一校验入口
 * <p>
 * 用于校验任务在Phase1阶段，新建、调度场景下允许的血缘类型与执行模式组合。
 * 仅允许指定血缘类型 + LIVE执行模式，不满足条件则抛出业务异常。
 * </p>
 */
public final class TaskExecutionPolicy {

    /**
     * 阶段允许的【实时执行】血缘类型集合
     * 支持：原始任务、重跑任务、评审返工任务
     */
    private static final EnumSet<TaskLineageType> PHASE_ONE_LIVE_TYPES = EnumSet.of(
            TaskLineageType.ORIGINAL,
            TaskLineageType.RERUN,
            TaskLineageType.REVIEW_REWORK
    );

    /**
     * 私有构造函数，工具类禁止实例化
     */
    private TaskExecutionPolicy() {
    }

    /**
     * 校验新建/调度任务的执行语义合法性
     * <p>
     * 校验规则：
     * 1. 任务的执行模式必须为 LIVE（实时模式）
     * 2. 任务血缘类型必须属于 {@link #PHASE_ONE_LIVE_TYPES} 集合
     * 3. 血缘类型、执行模式字段不能为空、枚举值必须合法
     * </p>
     * @param task 待创建或调度的任务实体
     * @throws BusinessException 枚举解析失败 / 执行模式或血缘类型不满足Phase1要求时抛出
     */
    public static void requireSupported(TaskEntity task) {
        TaskLineageType lineageType;
        TaskExecutionMode executionMode;
        try {
            // 从任务实体字符串字段解析对应枚举，空值/非法字符串会抛出异常
            lineageType = TaskLineageType.valueOf(task.getLineageType());
            executionMode = TaskExecutionMode.valueOf(task.getExecutionMode());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务执行语义不完整或不合法");
        }

        // 校验：必须是LIVE模式，且血缘类型在Phase1允许集合内
        if (executionMode != TaskExecutionMode.LIVE || !PHASE_ONE_LIVE_TYPES.contains(lineageType)) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前阶段不支持该任务执行类型或模式");
        }
    }
}