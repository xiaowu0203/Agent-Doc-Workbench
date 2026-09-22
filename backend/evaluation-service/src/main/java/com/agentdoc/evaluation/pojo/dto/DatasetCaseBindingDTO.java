package com.agentdoc.evaluation.pojo.dto;
import jakarta.validation.constraints.NotNull;
public record DatasetCaseBindingDTO(@NotNull Long testCaseVersionId, @NotNull Integer sortOrder, Boolean enabled) { }
