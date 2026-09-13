package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "系统 Skill 创建参数")
public record SystemSkillCreateDTO(
        @NotBlank @Size(max = 100) @Schema(description = "Skill 名称") String name,
        @NotBlank @Size(max = 100) @Schema(description = "前端展示名称") String displayName,
        @NotBlank @Size(max = 500) @Schema(description = "Skill 描述") String description) {
}
