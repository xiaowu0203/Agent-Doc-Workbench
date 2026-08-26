package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Agent Skill 绑定替换参数")
public record AgentSkillReplaceDTO(
        @NotNull @Size(max = 20) @Schema(description = "Skill 版本 ID 列表") List<Long> skillVersionIds) {
}
