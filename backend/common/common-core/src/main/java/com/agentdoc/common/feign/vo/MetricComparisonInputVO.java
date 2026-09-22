package com.agentdoc.common.feign.vo;

import java.util.List;

/**
 * Phase 4 可直接消费的标准 Metric 对比输入。
 */
public record MetricComparisonInputVO(List<MetricComparisonRowVO> rows) {
}
