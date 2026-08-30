package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.enums.SkillSelectionMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Agent 配置信息")
public record AgentVO(
        @Schema(description = "Agent ID") Long id,
        @Schema(description = "空间 ID") Long spaceId,
        @Schema(description = "Agent 名称") String name,
        @Schema(description = "Agent 描述") String description,
        @Schema(description = "系统提示词") String systemPrompt,
        @Schema(description = "模型 ID") Long modelId,
        @Schema(description = "Skill 选择模式") SkillSelectionMode skillSelectionMode,
        @Schema(description = "Skill Router 模型 ID；为空时复用 Agent 主模型") Long skillRouterModelId,
        @Schema(description = "是否启用外部 MCP") Boolean externalMcpEnabled,
        @Schema(description = "Token 预算上限") Long tokenBudget,
        @Schema(description = "文档访问范围") String documentScope,
        @Schema(description = "MCP 工具白名单") List<String> toolWhitelist,
        @Schema(description = "最大工具迭代次数") Integer maxIterations,
        @Schema(description = "执行超时时间（秒）") Integer executionTimeoutSeconds,
        @Schema(description = "配置版本号") Long configVersion,
        @Schema(description = "状态") AgentStatus status,
        @Schema(description = "创建人用户 ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdAt) {
}
