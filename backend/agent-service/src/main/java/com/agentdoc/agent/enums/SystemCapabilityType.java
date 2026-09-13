package com.agentdoc.agent.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "系统能力类型")
public enum SystemCapabilityType {
    @Schema(description = "系统 Skill")
    SKILL,
    @Schema(description = "Agent 模板")
    AGENT_TEMPLATE,
    @Schema(description = "MCP 模板")
    MCP_TEMPLATE
}
