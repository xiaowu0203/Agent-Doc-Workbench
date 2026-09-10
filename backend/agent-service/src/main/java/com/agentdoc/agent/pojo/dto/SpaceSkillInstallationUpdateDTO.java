package com.agentdoc.agent.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "空间系统 Skill 安装更新参数")
public record SpaceSkillInstallationUpdateDTO(
        @Schema(description = "升级后固定的已发布 Skill 版本 ID") Long skillVersionId,
        @Schema(description = "是否启用") Boolean enabled) {
}
