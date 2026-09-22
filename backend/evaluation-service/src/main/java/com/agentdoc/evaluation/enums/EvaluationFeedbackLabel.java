package com.agentdoc.evaluation.enums;

/**
 * 评估反馈标签枚举。
 * <p>
 * 用于人工对评估结果做复核标记，代表人工判定结论。
 * </p>
 */
public enum EvaluationFeedbackLabel {
    /** 接受，评估结果符合预期 */
    ACCEPTED,
    /** 拒绝，评估结果不符合预期 */
    REJECTED,
    /** 需要修改，结果存在问题，待调整后重评估 */
    NEEDS_CHANGES,
    /** 不适用，当前评估项不适合做判定 */
    NOT_APPLICABLE
}
