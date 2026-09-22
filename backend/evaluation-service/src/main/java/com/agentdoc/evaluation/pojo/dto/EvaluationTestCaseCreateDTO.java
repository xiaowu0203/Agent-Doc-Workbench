package com.agentdoc.evaluation.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EvaluationTestCaseCreateDTO(
        @NotNull Long spaceId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 1000) String description) {
}
