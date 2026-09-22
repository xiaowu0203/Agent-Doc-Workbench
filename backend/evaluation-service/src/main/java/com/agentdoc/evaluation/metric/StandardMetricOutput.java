package com.agentdoc.evaluation.metric;

import java.util.List;

/**
 * 一个标准 Metric 值以及它引用的证据 key。
 * <p>
 * 评估器输出载体，将指标值与关联的证据引用key打包；
 * 用于上层一次性提交指标+证据关联关系。
 * </p>
 * @param value 标准化指标值本体
 * @param evidenceReferenceKeys 关联证据引用key列表，关联EvidenceReferenceValue
 */
public record StandardMetricOutput(StandardMetricValue value, List<String> evidenceReferenceKeys) {
}
