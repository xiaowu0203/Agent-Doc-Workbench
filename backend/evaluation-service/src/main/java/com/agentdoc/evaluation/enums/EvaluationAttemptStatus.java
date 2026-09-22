package com.agentdoc.evaluation.enums;

/**
 * CaseAttempt 执行状态枚举。
 * <p>
 * 描述单条评估用例尝试任务的全生命周期状态；
 * active() 用于判断任务是否处于活跃进行中状态，可被取消。
 * </p>
 */
public enum EvaluationAttemptStatus {
    /** 已创建，等待开始回放 */
    CREATED,
    /** 回放任务已创建，待调度执行 */
    REPLAY_CREATED,
    /** 回放任务正在运行 */
    REPLAY_RUNNING,
    /** 回放完成，评估器正在执行评估计算 */
    EVALUATING,
    /** 整个用例尝试执行完成（评估成功） */
    COMPLETED,
    /** 回放阶段执行失败 */
    REPLAY_FAILED,
    /** 评估器执行阶段失败 */
    EVALUATOR_FAILED,
    /** 取消请求已接收，等待中断任务 */
    CANCEL_PENDING,
    /** 任务已成功取消 */
    CANCELED;

    /**
     * 判断当前状态是否为活跃状态（任务尚未终态，支持取消操作）
     * @return true：任务处于进行中，false：任务已结束/失败/已取消
     */
    public boolean active() {
        return this == CREATED || this == REPLAY_CREATED || this == REPLAY_RUNNING
                || this == EVALUATING || this == CANCEL_PENDING;
    }
}
