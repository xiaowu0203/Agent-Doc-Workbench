package com.agentdoc.common.feign.vo;

/**
 * 一个 Run 在某个对齐键上的值或显式缺失状态。
 *
 * @param runId EvaluationRun ID
 * @param metric 有效 Metric；缺失时为空
 * @param missingReason 缺失原因；存在 Metric 时为空
 */
public record MetricComparisonValueVO(
        Long runId,
        StandardMetricVO metric,
        String missingReason) {
}
