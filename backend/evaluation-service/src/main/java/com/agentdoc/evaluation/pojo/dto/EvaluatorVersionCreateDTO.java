package com.agentdoc.evaluation.pojo.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record EvaluatorVersionCreateDTO(@NotNull Long evaluatorId, @NotNull Integer configSchemaVersion,
        @NotBlank String configJson, @NotNull Integer resultSchemaVersion) { }
