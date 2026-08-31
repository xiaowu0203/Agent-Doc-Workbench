package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.agentdoc.agent.constant.McpConstant.MAX_BINDINGS_PER_AGENT;

@Schema(description = "Agent MCP 全量替换参数")
public record AgentMcpBindingReplaceDTO(
        @Schema(description = "替换后的完整 MCP 绑定列表")
        @NotNull
        @Size(max = MAX_BINDINGS_PER_AGENT)
        List<@Valid AgentMcpBindingItemDTO> bindings) {
}
