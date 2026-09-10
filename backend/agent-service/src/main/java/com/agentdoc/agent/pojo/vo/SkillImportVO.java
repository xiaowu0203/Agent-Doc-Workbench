package com.agentdoc.agent.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Skill ZIP 导入结果")
public record SkillImportVO(
        @Schema(description = "自动创建的 Skill") SkillVO skill,
        @Schema(description = "自动创建的草稿版本") SkillVersionVO version) {
}
