package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.enums.SkillSelectionMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Agent 列表卡片信息。
 */
@Schema(description = "Agent 列表卡片信息")
public record AgentCardVO(
        @Schema(description = "Agent ID") Long id,
        @Schema(description = "空间 ID") Long spaceId,
        @Schema(description = "Agent 名称") String name,
        @Schema(description = "Agent 描述") String description,
        @Schema(description = "模型 ID") Long modelId,
        @Schema(description = "模型展示名称") String modelDisplayName,
        @Schema(description = "Skill 选择模式") SkillSelectionMode skillSelectionMode,
        @Schema(description = "是否启用外部 MCP") Boolean externalMcpEnabled,
        @Schema(description = "Token 预算上限") Long tokenBudget,
        @Schema(description = "最大工具迭代次数") Integer maxIterations,
        @Schema(description = "执行超时时间（秒）") Integer executionTimeoutSeconds,
        @Schema(description = "配置版本号") Long configVersion,
        @Schema(description = "状态") AgentStatus status,
        @Schema(description = "当前启用的 Skill 绑定数量") long skillCount,
        @Schema(description = "当前启用的 MCP 绑定数量") long mcpCount,
        @Schema(description = "全部绑定 Skill 声明工具与绑定 MCP 已发现工具的去重数量；不受 ROUTER 当次选择影响")
        long toolCount,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "最近更新时间") LocalDateTime updatedAt) {
}
