package com.agentdoc.evaluation.enums;

/**
 * 规则评价结论枚举；FAILED 是质量结论，ERROR 才是评价基础设施错误。
 * <p>
 * 区分评估规则本身的质量判定结果与执行异常：FAILED 代表业务校验不通过；ERROR 代表评估执行发生系统/基础设施异常。
 * </p>
 */
public enum EvaluationResultStatus {
    /** 评估规则校验通过 */
    PASSED,
    /** 评估规则校验不通过（业务质量结论） */
    FAILED,
    /** 评估执行异常（基础设施/内部错误） */
    ERROR,
    /** 该评估规则被跳过，未执行 */
    SKIPPED
}
