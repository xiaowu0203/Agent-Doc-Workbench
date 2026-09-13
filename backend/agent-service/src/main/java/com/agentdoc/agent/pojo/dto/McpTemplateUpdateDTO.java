package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import static com.agentdoc.agent.constant.McpConstant.MAX_DISPLAY_NAME_LENGTH;

@Schema(description = "系统 MCP 模板更新参数")
public record McpTemplateUpdateDTO(
        @NotBlank @Size(max = MAX_DISPLAY_NAME_LENGTH)
        @Schema(description = "展示名称") String displayName,
        @Size(max = 500) @Schema(description = "模板说明") String description,
        @NotNull @Min(0) @Max(1) @Schema(description = "状态：0 停用 / 1 启用") Integer status) {
}
