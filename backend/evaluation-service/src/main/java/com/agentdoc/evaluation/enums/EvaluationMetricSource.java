package com.agentdoc.evaluation.enums;

/**
 * 标准 Metric 权威来源枚举。
 * <p>
 * 标识指标数据的产生主体，区分来自评估器计算或是执行过程原生采集。
 * </p>
 */
public enum EvaluationMetricSource {
    /** 由评估器计算产出的指标 */
    EVALUATOR,
    /** 执行阶段原生采集的指标 */
    EXECUTION
}
