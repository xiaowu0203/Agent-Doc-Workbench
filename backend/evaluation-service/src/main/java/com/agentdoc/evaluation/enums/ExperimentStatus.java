package com.agentdoc.evaluation.enums;

/**
 * 离线实验生命周期状态。
 */
public enum ExperimentStatus {
    CREATED,
    STARTING,
    RUNNING,
    PAUSED,
    CANCEL_PENDING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    CANCELED,
    FAILED;

    /**
     * 判断当前状态是否为终态。
     *
     * @return true 表示实验已经结束
     */
    public boolean terminal() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == CANCELED || this == FAILED;
    }
}
