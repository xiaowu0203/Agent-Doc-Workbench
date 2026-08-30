package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Skill 创建参数")
public record SkillCreateDTO(
        @NotNull @Schema(description = "空间 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long spaceId,
        @NotBlank @Size(max = 100) @Schema(description = "Skill 名称", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @NotBlank @Size(max = 100) @Schema(description = "前端展示名称", requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @NotBlank @Size(max = 500) @Schema(description = "Skill 描述", requiredMode = Schema.RequiredMode.REQUIRED) String description) {
}
