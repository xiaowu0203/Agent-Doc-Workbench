package com.agentdoc.evaluation.enums;

/**
 * 评估反馈来源类型枚举。
 * <p>
 * 标记评估复核反馈的产生渠道，区分人工直接录入或来自变更请求。
 * </p>
 */
public enum EvaluationFeedbackSourceType {
    /** 人工直接提交的反馈 */
    MANUAL,
    /** 反馈来源于变更请求 */
    CHANGE_REQUEST
}