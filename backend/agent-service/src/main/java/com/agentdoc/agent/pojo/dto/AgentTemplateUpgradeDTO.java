package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Agent 模板升级参数")
public record AgentTemplateUpgradeDTO(
        @NotNull @Schema(description = "目标已发布模板版本 ID") Long targetVersionId,
        @NotNull @Schema(description = "仅预览，不应用变更") Boolean previewOnly,
        @Valid @Schema(description = "发生冲突时管理员确认的最终 Agent 配置") AgentUpdateDTO resolvedConfig,
        @Size(max = 20) @Schema(description = "发生 Skill 冲突时确认的最终版本 ID 列表")
        List<Long> resolvedSkillVersionIds,
        @Valid @Size(max = 10) @Schema(description = "发生 MCP 冲突时确认的最终空间 MCP 绑定")
        List<@NotNull @Valid AgentMcpBindingItemDTO> resolvedMcpBindings) { }
