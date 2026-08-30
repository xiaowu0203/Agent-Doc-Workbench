package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Skill 更新参数")
public record SkillUpdateDTO(
        @NotBlank @Size(max = 100) @Schema(description = "Skill 名称") String name,
        @NotBlank @Size(max = 100) @Schema(description = "前端展示名称") String displayName,
        @NotBlank @Size(max = 500) @Schema(description = "Skill 描述") String description) {
}
