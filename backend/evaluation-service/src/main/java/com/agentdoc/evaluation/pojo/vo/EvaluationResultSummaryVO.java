package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "EvaluationResult 轻量摘要")
public record EvaluationResultSummaryVO(
        @Schema(description = "EvaluationResult ID") Long id,
        @Schema(description = "评估器版本 ID") Long evaluatorVersionId,
        @Schema(description = "评估器名称") String evaluatorName,
        @Schema(description = "评估器版本号") Integer evaluatorVersionNo,
        @Schema(description = "同一评估器的评价尝试序号") Integer evaluationAttemptNo,
        @Schema(description = "是否为该评估器当前结果尝试") boolean currentEvaluationResultAttempt,
        @Schema(description = "结果状态") String status,
        @Schema(description = "非权威便捷展示分数") BigDecimal score,
        @Schema(description = "稳定摘要码") String summaryCode,
        @Schema(description = "评价 Trace ID") String traceId,
        @Schema(description = "评价 Span ID") String spanId,
        @Schema(description = "开始时间") LocalDateTime startedAt,
        @Schema(description = "结束时间") LocalDateTime finishedAt
) { }
