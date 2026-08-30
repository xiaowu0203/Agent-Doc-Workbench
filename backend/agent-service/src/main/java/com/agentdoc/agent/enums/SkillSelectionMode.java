package com.agentdoc.agent.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent Skill 选择模式")
public enum SkillSelectionMode {
    /** 暴露全部已绑定 Skill 的轻量目录。 */
    ALL_BOUND,
    /** 先由 Router 选择候选 Skill。 */
    ROUTER
}
