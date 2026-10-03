package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 修改测试用例草稿预期配置，来源快照保持冻结。 */
@Schema(description = "修改测试用例草稿配置")
public record TestCaseVersionUpdateDTO(
        @NotNull @Schema(description = "预期结果 Schema 版本") Integer expectedSchemaVersion,
        @NotBlank @Schema(description = "预期结果 JSON") String expectedJson,
        @NotBlank @Size(max = 32) @Schema(description = "来源类型，首版仅支持 LIVE") String sourceType,
        @Size(max = 1000) @Schema(description = "脱敏与裁剪说明") String sanitizationNote
) {}
