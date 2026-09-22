package com.agentdoc.evaluation.enums;

/**
 * 标准 Metric 跨运行比较方向。
 * <p>
 * 定义指标优劣判定规则，用于多轮评估结果对比排序。
 * </p>
 */
public enum EvaluationMetricDirection {
    /** 数值越高越好 */
    HIGHER_IS_BETTER,
    /** 数值越低越好 */
    LOWER_IS_BETTER,
    /** 中性指标，无优劣方向，仅展示原始值 */
    NEUTRAL
}
