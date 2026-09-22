package com.agentdoc.evaluation.enums;

/**
 * Evaluation WorkerCapability Segment 状态枚举。
 * <p>
 * 描述评估工作节点能力分片的生命周期状态，用于控制能力是否可参与调度。
 * </p>
 */
public enum EvaluationWorkerCapabilityStatus {
    /** 激活状态，可正常参与评估任务调度 */
    ACTIVE,
    /** 已过期，能力不再可用 */
    EXPIRED,
    /** 已撤销，能力被主动收回 */
    REVOKED
}
