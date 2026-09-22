package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * 获取标准 Metric 对比输入的查询契约。
 *
 * @param spaceId 所属空间 ID
 * @param runIds 需要比较的 EvaluationRun ID
 * @param testCaseVersionIds 可选的测试用例版本过滤
 * @param metricKeys 可选的 Metric key 过滤
 */
public record MetricComparisonQueryDTO(
        Long spaceId,
        List<Long> runIds,
        List<Long> testCaseVersionIds,
        List<String> metricKeys) {
}
