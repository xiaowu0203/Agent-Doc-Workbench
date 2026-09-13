package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.agentdoc.agent.constant.McpConstant.MAX_MODEL_TOOL_NAME_LENGTH;
import static com.agentdoc.agent.constant.McpConstant.MAX_TOOL_WHITELIST_SIZE;

@Schema(description = "Agent 模板的系统 MCP 引用")
public record AgentTemplateMcpDTO(
        @NotNull @Schema(description = "固定的 MCP 模板版本 ID") Long mcpTemplateVersionId,
        @Size(max = MAX_TOOL_WHITELIST_SIZE)
        @Schema(description = "默认远端工具白名单；null 表示不额外限制")
        List<@NotBlank @Size(max = MAX_MODEL_TOOL_NAME_LENGTH) String> toolWhitelist) {
}
