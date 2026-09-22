package com.agentdoc.evaluation.pojo.dto;

import com.agentdoc.evaluation.enums.EvaluationFeedbackLabel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EvaluationFeedbackCreateDTO(
        @NotNull Long spaceId,
        Long caseRunId,
        Long taskId,
        Long executionId,
        @NotNull EvaluationFeedbackLabel label,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal score,
        @Size(max = 2000) String comment) {
}
