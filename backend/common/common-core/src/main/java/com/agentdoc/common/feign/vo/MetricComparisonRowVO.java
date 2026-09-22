package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * 按 TestCaseVersion 与 metricKey 对齐的一行对比输入。
 */
public record MetricComparisonRowVO(
        Long testCaseVersionId,
        String metricKey,
        Integer contractVersion,
        String valueType,
        String unit,
        String direction,
        String source,
        List<MetricComparisonValueVO> values) {
}
