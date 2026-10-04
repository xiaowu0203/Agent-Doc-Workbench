package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 修改评估器草稿配置，不允许更换所属资源和实现版本。 */
@Schema(description = "修改评估器草稿配置")
public record EvaluatorVersionUpdateDTO(
        @NotNull @Schema(description = "配置 Schema 版本") Integer configSchemaVersion,
        @NotBlank @Schema(description = "评估器配置 JSON") String configJson,
        @NotNull @Schema(description = "结果 Schema 版本") Integer resultSchemaVersion
) {}
