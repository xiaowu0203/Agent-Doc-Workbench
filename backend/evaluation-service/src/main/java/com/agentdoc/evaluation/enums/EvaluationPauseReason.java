package com.agentdoc.evaluation.enums;

/**
 * 可由有权限用户恢复的暂停原因枚举。
 * <p>
 * 标记评估任务暂停的原因，这类暂停支持权限用户手动恢复继续执行。
 * </p>
 */
public enum EvaluationPauseReason {
    /** 调度服务不可用 */
    DISPATCH_UNAVAILABLE,
    /** 授权凭证过期 */
    AUTHORIZATION_EXPIRED,
    /** 对账补偿流程阻塞 */
    RECONCILIATION_BLOCKED
}
