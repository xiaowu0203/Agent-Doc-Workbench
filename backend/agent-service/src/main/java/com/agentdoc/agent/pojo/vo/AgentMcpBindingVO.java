package com.agentdoc.agent.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Agent MCP 绑定信息")
public record AgentMcpBindingVO(
        @Schema(description = "绑定 ID") Long id,
        @Schema(description = "Agent ID") Long agentId,
        @Schema(description = "MCP Server ID") Long mcpServerId,
        @Schema(description = "MCP Server 技术标识") String serverKey,
        @Schema(description = "MCP Server 展示名称") String displayName,
        @Schema(description = "远端原始工具白名单；null 表示不额外限制，空数组表示禁用全部工具")
        List<String> toolWhitelist,
        @Schema(description = "绑定是否启用") Boolean enabled) {
}
