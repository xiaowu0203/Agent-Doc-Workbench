package com.agentdoc.evaluation.pojo.dto;
import jakarta.validation.constraints.NotNull;
public record DatasetVersionCreateDTO(@NotNull Long datasetId) { }
