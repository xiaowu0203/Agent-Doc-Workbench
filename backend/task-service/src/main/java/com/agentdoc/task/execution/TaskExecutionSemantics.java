package com.agentdoc.task.execution;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.pojo.entity.TaskEntity;

/**
 * 任务血缘与执行模式的永久语义规则工具类
 * <p>定义任务血缘类型（lineage）和执行模式（mode）的合法配对约束，为全局基础校验规则，永久生效。</p>
 */
public final class TaskExecutionSemantics {
    /**
     * 私有构造，工具类禁止实例化
     */
    private TaskExecutionSemantics() {
    }

    /**
     * 校验任务血缘类型与执行模式的组合是否符合语义规则
     * <p>
     * 合法配对规则：
     * <ul>
     *     <li>ORIGINAL原始任务、RERUN重跑、REVIEW_REWORK评审返工、LEGACY_UNKNOWN遗留未知：只能使用 LIVE 实时执行模式</li>
     *     <li>REPLAY回放、EXPERIMENT实验任务：只能使用 ISOLATED 隔离执行模式</li>
     * </ul>
     * </p>
     * @param task 待校验任务实体
     * @throws BusinessException 血缘与模式组合非法，抛出CONFLICT冲突异常
     */
    public static void requireValid(TaskEntity task) {
        TaskLineageType lineage = lineage(task);
        TaskExecutionMode mode = mode(task);
        boolean valid = switch (lineage) {
            case ORIGINAL, RERUN, REVIEW_REWORK, LEGACY_UNKNOWN -> mode == TaskExecutionMode.LIVE;
            case REPLAY, EXPERIMENT -> mode == TaskExecutionMode.ISOLATED;
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务血缘与执行模式组合不合法");
        }
    }

    /**
     * 从任务实体读取并解析任务血缘类型枚举
     * @param task 任务实体
     * @return TaskLineageType 任务血缘枚举
     * @throws BusinessException 字段为空或枚举值不存在时抛出CONFLICT
     */
    static TaskLineageType lineage(TaskEntity task) {
        try {
            return TaskLineageType.valueOf(task.getLineageType());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务血缘类型不完整或不合法");
        }
    }

    /**
     * 从任务实体读取并解析任务执行模式枚举
     * @param task 任务实体
     * @return TaskExecutionMode 执行模式枚举
     * @throws BusinessException 字段为空或枚举值不存在时抛出CONFLICT
     */
    static TaskExecutionMode mode(TaskEntity task) {
        try {
            return TaskExecutionMode.valueOf(task.getExecutionMode());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务执行模式不完整或不合法");
        }
    }
}
