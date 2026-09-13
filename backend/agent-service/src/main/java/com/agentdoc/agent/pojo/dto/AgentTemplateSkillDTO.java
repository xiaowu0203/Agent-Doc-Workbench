package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Agent 模板系统 Skill 引用")
public record AgentTemplateSkillDTO(
        @NotNull @Schema(description = "系统 Skill ID") Long skillId,
        @NotNull @Schema(description = "固定 Skill 版本 ID") Long skillVersionId) { }
