package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.AgentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Skill 关联 Agent 信息")
public record SkillAgentBindingVO(
        @Schema(description = "绑定 ID") Long id,
        @Schema(description = "Agent ID") Long agentId,
        @Schema(description = "Agent 名称") String agentName,
        @Schema(description = "Agent 状态") AgentStatus agentStatus,
        @Schema(description = "绑定的 Skill 版本 ID") Long skillVersionId,
        @Schema(description = "绑定的版本号") Integer versionNo,
        @Schema(description = "绑定是否启用") Boolean enabled) {
}
