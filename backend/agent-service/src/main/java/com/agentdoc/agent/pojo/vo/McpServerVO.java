package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.McpAuthType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "MCP Server 信息")
public record McpServerVO(
        @Schema(description = "MCP Server ID") Long id,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "空间内唯一技术标识") String serverKey,
        @Schema(description = "展示名称") String displayName,
        @Schema(description = "公网 HTTPS 端点") String endpointUrl,
        @Schema(description = "认证类型") McpAuthType authType,
        @Schema(description = "是否已配置认证令牌") boolean authConfigured,
        @Schema(description = "配置版本号") Long configVersion,
        @Schema(description = "状态：0 禁用 / 1 启用") Integer status) {
}
