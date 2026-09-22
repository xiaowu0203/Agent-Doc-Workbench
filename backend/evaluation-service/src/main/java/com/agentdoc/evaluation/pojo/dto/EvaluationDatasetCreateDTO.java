package com.agentdoc.evaluation.pojo.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
@Schema(description = "创建数据集")
public record EvaluationDatasetCreateDTO(@NotNull Long spaceId,
        @NotBlank @Size(max = 200) String name, @Size(max = 1000) String description) { }
