package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EvaluationFeedbackVO(
        Long id,
        Long spaceId,
        Long runId,
        Long caseRunId,
        Long taskId,
        Long executionId,
        String sourceType,
        String sourceBusinessId,
        String sourceHash,
        String label,
        BigDecimal score,
        String comment,
        String factsJson,
        Long createdBy,
        LocalDateTime createdAt) {

    public static EvaluationFeedbackVO from(EvaluationFeedbackEntity value) {
        return new EvaluationFeedbackVO(value.getId(), value.getSpaceId(), value.getRunId(), value.getCaseRunId(),
                value.getTaskId(), value.getExecutionId(), value.getSourceType(), value.getSourceBusinessId(),
                value.getSourceHash(), value.getLabel(), value.getScore(), value.getComment(), value.getFactsJson(),
                value.getCreatedBy(), value.getCreatedAt());
    }
}
