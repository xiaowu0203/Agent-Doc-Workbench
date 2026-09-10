package com.agentdoc.agent.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 空间 Agent 能力统计。
 */
@Schema(description = "空间 Agent 能力统计")
public record AgentOverviewStatsVO(

        @Schema(description = "启用的 Agent 数量")
        Long activeAgentCount,

        @Schema(description = "启用的 Skill 数量")
        Long activeSkillCount,

        @Schema(description = "启用的外部 MCP 数量")
        Long enabledMcpCount
) {
}
