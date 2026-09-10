package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.enums.McpConnectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "MCP Server 信息")
public record McpServerVO(
        @Schema(description = "MCP Server ID") Long id,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "空间内唯一技术标识") String serverKey,
        @Schema(description = "展示名称") String displayName,
        @Schema(description = "公网 HTTPS 端点") String endpointUrl,
        @Schema(description = "认证类型") McpAuthType authType,
        @Schema(description = "Query API Key 参数名") String authParamName,
        @Schema(description = "是否已配置认证令牌") boolean authConfigured,
        @Schema(description = "配置版本号") Long configVersion,
        @Schema(description = "状态：0 禁用 / 1 启用") Integer status,
        @Schema(description = "最近连接测试状态") McpConnectionStatus connectionStatus,
        @Schema(description = "最近一次连接测试完成时间") LocalDateTime lastTestedAt,
        @Schema(description = "最近一次握手与工具发现总耗时，毫秒") Long lastTestDurationMs,
        @Schema(description = "最近一次连接失败错误摘要") String lastTestError,
        @Schema(description = "最近一次成功发现的工具数量") Integer discoveredToolCount,
        @Schema(description = "当前工具快照发现时间") LocalDateTime toolsDiscoveredAt) {
}
