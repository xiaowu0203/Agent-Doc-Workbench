package com.agentdoc.evaluation.enums;

/** 人工原始质量评审的分组知情状态；UNKNOWN 不计入盲态覆盖。 */
public enum OnlineFeedbackBlindness {
    /** 已通过隐藏分组入口并核验无已知分组访问。 */
    BLINDED,
    /** 已知分组，偏差须沿更正链保留。 */
    NON_BLINDED,
    /** 不能确认是否知情。 */
    UNKNOWN
}
