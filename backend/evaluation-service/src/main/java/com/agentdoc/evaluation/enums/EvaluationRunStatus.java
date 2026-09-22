package com.agentdoc.evaluation.enums;

/**
 * 评估运行状态枚举。
 * <p>
 * 描述一次评估运行（EvaluationRun）完整生命周期；terminal() 判断是否进入不可变更的终态。
 * </p>
 */
public enum EvaluationRunStatus {
    /** 评估运行记录已创建，待调度 */
    CREATED,
    /** 正在分发任务 */
    DISPATCHING,
    /** 评估任务正在执行中 */
    RUNNING,
    /** 评估任务已暂停，可恢复 */
    PAUSED,
    /** 收到取消请求，等待任务中断 */
    CANCEL_PENDING,
    /** 全部用例执行完成，无错误 */
    COMPLETED,
    /** 整体完成，但部分用例存在错误 */
    COMPLETED_WITH_ERRORS,
    /** 评估运行已取消 */
    CANCELED,
    /** 评估运行整体失败 */
    FAILED;

    /**
     * 判断当前状态是否为终态，终态下评估运行不再变更
     * @return true 代表已结束，false 代表仍在进行中/暂停待处理
     */
    public boolean terminal() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == CANCELED || this == FAILED;
    }
}
