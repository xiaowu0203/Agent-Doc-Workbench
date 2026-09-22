package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.common.feign.vo.MetricEvidenceReferenceVO;
import com.agentdoc.common.feign.vo.StandardMetricVO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "不可变评估结果详情；Metric 是跨阶段比较契约，detailsJson 仅用于诊断")
public record EvaluationResultDetailVO(
        Long id,
        Long spaceId,
        Long runId,
        Long caseAttemptId,
        Long evaluatorVersionId,
        Integer evaluationAttemptNo,
        String status,
        BigDecimal score,
        String summaryCode,
        String detailsJson,
        String implementationVersion,
        String traceId,
        String spanId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        List<StandardMetricVO> metrics,
        List<MetricEvidenceReferenceVO> evidence,
        List<EvaluationFeedbackVO> feedback) {
}
