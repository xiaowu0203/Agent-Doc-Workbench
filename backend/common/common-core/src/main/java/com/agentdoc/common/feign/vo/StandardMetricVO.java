package com.agentdoc.common.feign.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 可聚合、可比较的标准业务 Metric。
 */
public record StandardMetricVO(
        Long id,
        Long spaceId,
        Long runId,
        Long caseRunId,
        Long caseAttemptId,
        Long testCaseVersionId,
        Long evaluationResultId,
        Long evaluatorVersionId,
        Integer contractVersion,
        String source,
        Long producerId,
        String metricKey,
        String valueType,
        BigDecimal numericValue,
        Boolean booleanValue,
        String stringValue,
        String unit,
        String direction,
        LocalDateTime createdAt,
        List<MetricEvidenceReferenceVO> evidence) {
}
