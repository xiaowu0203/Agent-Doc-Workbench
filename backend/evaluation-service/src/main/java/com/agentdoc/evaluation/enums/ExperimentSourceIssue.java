package com.agentdoc.evaluation.enums;

/** 创建前冻结来源核对的稳定问题码。 */
public enum ExperimentSourceIssue {
    /** 用例缺失、归属不一致、归档或未发布。 */
    CASE_UNAVAILABLE,
    /** 来源缺少必要的冻结身份。 */
    SOURCE_IDENTITY_MISSING,
    /** 启用用例不使用同一个执行配置快照。 */
    SOURCE_SNAPSHOT_MISMATCH,
    /** 代表来源与冻结身份不一致或当前不可回放。 */
    BASELINE_SOURCE_UNAVAILABLE
}
