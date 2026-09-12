package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.SkillSelectionMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Agent 模板版本")
public record AgentTemplateVersionVO(
        @Schema(description = "模板版本 ID") Long id,
        @Schema(description = "模板 ID") Long templateId,
        @Schema(description = "版本号") Integer versionNo,
        @Schema(description = "状态：0 草稿 / 1 已发布") Integer status,
        @Schema(description = "默认展示名称") String displayName,
        @Schema(description = "版本说明") String description,
        @Schema(description = "默认系统提示词") String systemPrompt,
        @Schema(description = "默认模型 ID") Long modelId,
        @Schema(description = "默认 Skill 选择模式") SkillSelectionMode skillSelectionMode,
        @Schema(description = "默认 Skill Router 模型 ID") Long skillRouterModelId,
        @Schema(description = "默认是否启用外部 MCP") Boolean externalMcpEnabled,
        @Schema(description = "默认 Token 预算") Long tokenBudget,
        @Schema(description = "默认模型工具白名单") List<String> toolWhitelist,
        @Schema(description = "默认最大迭代次数") Integer maxIterations,
        @Schema(description = "默认执行超时秒数") Integer executionTimeoutSeconds,
        @Schema(description = "固定的系统 Skill 版本引用") List<SkillReferenceVO> skills,
        @Schema(description = "固定的系统 MCP 模板引用") List<McpReferenceVO> mcps,
        @Schema(description = "创建人用户 ID") Long createdBy,
        @Schema(description = "发布人用户 ID") Long publishedBy,
        @Schema(description = "发布时间") LocalDateTime publishedAt,
        @Schema(description = "创建时间") LocalDateTime createdAt) {
    public record SkillReferenceVO(
            @Schema(description = "系统 Skill ID") Long skillId,
            @Schema(description = "固定 Skill 版本 ID") Long skillVersionId) { }

    public record McpReferenceVO(
            @Schema(description = "系统 MCP 模板 ID") Long mcpTemplateId,
            @Schema(description = "固定 MCP 模板版本 ID") Long mcpTemplateVersionId,
            @Schema(description = "默认远端工具白名单") List<String> toolWhitelist) { }
}
