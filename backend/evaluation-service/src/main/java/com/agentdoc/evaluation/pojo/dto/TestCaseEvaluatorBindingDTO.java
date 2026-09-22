package com.agentdoc.evaluation.pojo.dto;

import jakarta.validation.constraints.NotNull;

public record TestCaseEvaluatorBindingDTO(
        @NotNull Long evaluatorVersionId,
        String expectedJson,
        @NotNull Integer sortOrder) {
}
