package com.agentdoc.agent.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "系统 MCP 模板")
public record McpTemplateVO(
        @Schema(description = "模板 ID") Long id,
        @Schema(description = "稳定技术标识") String serverKey,
        @Schema(description = "展示名称") String displayName,
        @Schema(description = "模板说明") String description,
        @Schema(description = "状态：0 停用 / 1 启用") Integer status,
        @Schema(description = "最新已发布版本 ID") Long latestPublishedVersionId,
        @Schema(description = "最新已发布版本号") Integer latestPublishedVersionNo,
        @Schema(description = "平台最近发现的工具数量") Integer discoveredToolCount,
        @Schema(description = "平台工具发现时间") LocalDateTime toolsDiscoveredAt,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {
}
