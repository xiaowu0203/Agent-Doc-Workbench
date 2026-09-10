package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.McpAuthType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "系统 MCP 模板")
public record McpTemplateVO(
        @Schema(description = "模板 ID") Long id,
        @Schema(description = "稳定技术标识") String serverKey,
        @Schema(description = "展示名称") String displayName,
        @Schema(description = "模板说明") String description,
        @Schema(description = "默认公网 HTTPS 端点") String endpointUrl,
        @Schema(description = "认证类型") McpAuthType authType,
        @Schema(description = "Query API Key 参数名") String authParamName,
        @Schema(description = "模板配置版本") Long configVersion,
        @Schema(description = "状态：0 停用 / 1 启用") Integer status,
        @Schema(description = "平台最近发现的工具数量") Integer discoveredToolCount,
        @Schema(description = "平台工具发现时间") LocalDateTime toolsDiscoveredAt,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {
}
