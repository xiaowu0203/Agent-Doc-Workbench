package com.agentdoc.agent.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent Skill 绑定信息")
public record AgentSkillBindingVO(
        @Schema(description = "绑定 ID") Long id,
        @Schema(description = "Agent ID") Long agentId,
        @Schema(description = "Skill ID") Long skillId,
        @Schema(description = "Skill 名称") String skillName,
        @Schema(description = "Skill 版本 ID") Long skillVersionId,
        @Schema(description = "版本号") Integer versionNo,
        @Schema(description = "版本 SHA-256") String sha256,
        @Schema(description = "是否启用") Boolean enabled) {
}
