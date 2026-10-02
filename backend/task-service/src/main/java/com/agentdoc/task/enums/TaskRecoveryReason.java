package com.agentdoc.task.enums;

/** 恢复诊断稳定原因；不增加 Task 业务状态。 */
public enum TaskRecoveryReason {
    /** 本地 Task 不需要恢复。 */ NOT_REQUIRED,
    /** 原能力证明已经过期，正在核验。 */ CAPABILITY_EXPIRED,
    /** 恢复依赖未配置。 */ RECOVERY_SERVICE_UNCONFIGURED,
    /** 原签名证明非法。 */ SOURCE_CAPABILITY_INVALID,
    /** 冻结身份或执行关联不匹配。 */ RECOVERY_IDENTITY_MISMATCH,
    /** 恢复签发未成功。 */ RECOVERY_ISSUANCE_FAILED,
    /** 远端依赖不可用或任务不存在。 */ REMOTE_TASK_UNAVAILABLE,
    /** 远端仍活动，不能伪造终态。 */ REMOTE_TASK_ACTIVE,
    /** 草稿版本或暂存归属冲突。 */ DRAFT_VERSION_CONFLICT,
    /** 锁忙、丢失租期或处理预算不足。 */ RECOVERY_CAPACITY_EXCEEDED,
    /** 本地完整终态写回失败。 */ RECOVERY_WRITEBACK_FAILED,
    /** 已恢复同一任务终态。 */ RECOVERED
}
