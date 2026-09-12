package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.McpAuthType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "MCP 模板版本")
public record McpTemplateVersionVO(
        @Schema(description = "模板版本 ID") Long id,
        @Schema(description = "MCP 模板 ID") Long templateId,
        @Schema(description = "版本号") Integer versionNo,
        @Schema(description = "状态：0 草稿 / 1 已发布") Integer status,
        @Schema(description = "安装后的默认展示名称") String displayName,
        @Schema(description = "默认公网 HTTPS 端点") String endpointUrl,
        @Schema(description = "认证类型") McpAuthType authType,
        @Schema(description = "Query API Key 参数名") String authParamName,
        @Schema(description = "创建人用户 ID") Long createdBy,
        @Schema(description = "发布人用户 ID") Long publishedBy,
        @Schema(description = "发布时间") LocalDateTime publishedAt,
        @Schema(description = "创建时间") LocalDateTime createdAt) {
}
