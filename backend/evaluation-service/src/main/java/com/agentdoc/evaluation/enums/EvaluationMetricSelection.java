package com.agentdoc.evaluation.enums;

/**
 * Metric 查询投影视图枚举。
 * <p>
 * 控制指标查询时读取的数据视图，区分取当前生效指标还是全量历史指标。
 * </p>
 */
public enum EvaluationMetricSelection {
    /**
     * 当前 CaseAttempt 及每个 EvaluatorVersion 的最新 EvaluationResult 所产生的 Metric。
     */
    EFFECTIVE,
    /**
     * 所有不可变 Metric 历史记录。
     */
    HISTORY
}
