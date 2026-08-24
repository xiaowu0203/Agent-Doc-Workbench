package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.AgentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Agent 脱敏视图，不返回 MCP 密文或明文。
 */
@Schema(description = "Agent 信息")
public record AgentVO(
        @Schema(description = "Agent ID") Long id,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "Agent 名称") String name,
        @Schema(description = "Agent 描述") String description,
        @Schema(description = "关联 OAuth2 客户端 ID") String clientId,
        @Schema(description = "关联模型 ID") Long modelId,
        @Schema(description = "工具白名单，逗号分隔") String toolWhitelist,
        @Schema(description = "可读写文档范围，JSON 格式") String docScope,
        @Schema(description = "Token 预算上限") Long tokenBudget,
        @Schema(description = "是否已配置 MCP") boolean mcpConfigured,
        @Schema(description = "Agent 状态") AgentStatus status,
        @Schema(description = "创建人用户 ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdAt) {
}
