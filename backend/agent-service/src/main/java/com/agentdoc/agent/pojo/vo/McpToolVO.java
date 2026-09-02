package com.agentdoc.agent.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** MCP Server 发现的远端工具定义。 */
@Schema(description = "MCP Server 发现的远端工具定义")
public record McpToolVO(
        @Schema(description = "远端原始工具名") String name,
        @Schema(description = "工具描述") String description,
        @Schema(description = "工具输入 JSON Schema") String inputSchema) {
}
