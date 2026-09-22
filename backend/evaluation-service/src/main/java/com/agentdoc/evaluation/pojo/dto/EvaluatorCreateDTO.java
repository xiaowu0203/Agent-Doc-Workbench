package com.agentdoc.evaluation.pojo.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record EvaluatorCreateDTO(@NotNull Long spaceId, @NotBlank @Size(max=200) String name,
        @NotBlank @Size(max=64) String evaluatorKey, @Size(max=1000) String description) { }
