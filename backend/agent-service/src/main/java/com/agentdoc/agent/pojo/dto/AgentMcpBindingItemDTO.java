package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.agentdoc.agent.constant.McpConstant.MAX_MODEL_TOOL_NAME_LENGTH;
import static com.agentdoc.agent.constant.McpConstant.MAX_TOOL_WHITELIST_SIZE;

@Schema(description = "Agent MCP 单项绑定参数")
public record AgentMcpBindingItemDTO(
        @Schema(description = "MCP Server ID")
        @NotNull
        Long mcpServerId,

        @Schema(description = "允许调用的远端原始工具名；null 表示不额外限制，空数组表示禁用全部工具")
        @Size(max = MAX_TOOL_WHITELIST_SIZE)
        List<@NotBlank @Size(max = MAX_MODEL_TOOL_NAME_LENGTH) String> toolWhitelist) {
}
