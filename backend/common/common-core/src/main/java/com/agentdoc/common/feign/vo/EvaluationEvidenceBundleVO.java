package com.agentdoc.common.feign.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 六类确定性 Evaluator 共用的最小权威事实聚合。 */
public record EvaluationEvidenceBundleVO(
        Long taskId,
        Long spaceId,
        Integer taskStatusCode,
        String taskStatus,
        String resultSummary,
        Long executionId,
        String executionStatus,
        String traceId,
        String spanId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long inputTokens,
        Long cachedInputTokens,
        Long outputTokens,
        BigDecimal cost,
        String currency,
        Integer retryCount,
        long toolCallCount,
        long failedToolCallCount,
        long externalMcpCallCount,
        long changeRequestCount,
        List<EvaluationArtifactEvidenceVO> artifacts) {
}
