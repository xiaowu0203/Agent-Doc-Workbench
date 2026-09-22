package com.agentdoc.task.execution;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.pojo.entity.TaskEntity;

import java.util.EnumSet;

/**
 * 任务执行可用范围校验工具类
 * <p>定义当前版本支持创建与调度的任务谱系+执行模式组合，不在白名单内的任务直接拒绝执行。</p>
 */
public final class TaskExecutionAvailability {
    /**
     * LIVE实时执行模式允许的任务谱系类型：原始任务、重跑、评审返工
     */
    private static final EnumSet<TaskLineageType> LIVE_TYPES = EnumSet.of(
            TaskLineageType.ORIGINAL, TaskLineageType.RERUN, TaskLineageType.REVIEW_REWORK);

    /**
     * 私有构造，工具类禁止实例化
     */
    private TaskExecutionAvailability() {
    }

    /**
     * 校验该任务是否在当前版本开放的执行范围内，不满足则抛出业务冲突异常
     * <p>
     * 允许规则：
     * <ul>
     *     <li>LIVE实时模式：谱系为 ORIGINAL / RERUN / REVIEW_REWORK</li>
     *     <li>ISOLATED隔离模式：谱系仅允许 REPLAY 回放</li>
     * </ul>
     * </p>
     * @param task 待校验任务实体
     * @throws BusinessException 任务类型不在开放范围内时抛出 CONFLICT
     */
    public static void requireAvailable(TaskEntity task) {
        // 基础任务语义合法性校验
        TaskExecutionSemantics.requireValid(task);
        // 提取任务谱系类型
        TaskLineageType lineage = TaskExecutionSemantics.lineage(task);
        // 提取任务执行模式
        TaskExecutionMode mode = TaskExecutionSemantics.mode(task);
        // LIVE模式，谱系在允许集合内，校验通过
        if (mode == TaskExecutionMode.LIVE && LIVE_TYPES.contains(lineage)) {
            return;
        }
        // ISOLATED隔离模式，仅允许REPLAY回放任务
        if (mode == TaskExecutionMode.ISOLATED && lineage == TaskLineageType.REPLAY) {
            return;
        }
        // 不在支持组合范围内，拒绝执行
        throw new BusinessException(ErrorCode.CONFLICT, "当前版本尚未开放该任务执行类型");
    }
}
