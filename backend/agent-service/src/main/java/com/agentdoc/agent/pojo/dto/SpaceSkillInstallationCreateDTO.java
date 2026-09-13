package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "空间安装系统 Skill 参数")
public record SpaceSkillInstallationCreateDTO(
        @NotNull @Schema(description = "系统 Skill ID") Long skillId,
        @NotNull @Schema(description = "固定的已发布 Skill 版本 ID") Long skillVersionId) {
}
