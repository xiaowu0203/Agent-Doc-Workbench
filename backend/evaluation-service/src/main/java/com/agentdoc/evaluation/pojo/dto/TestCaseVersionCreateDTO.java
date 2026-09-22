package com.agentdoc.evaluation.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestCaseVersionCreateDTO(
        @NotNull Long testCaseId,
        @NotNull Long sourceTaskId,
        @NotNull Integer expectedSchemaVersion,
        @NotBlank String expectedJson,
        @NotBlank @Size(max = 32) String sourceType,
        @Size(max = 1000) String sanitizationNote) {
}
