package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.vo.AgentOverviewStatsVO;
import com.agentdoc.agent.service.AgentOverviewService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 能力概览接口。
 */
@Tag(name = "Agent 能力概览", description = "空间 Agent、Skill、MCP 能力统计")
@RestController
@RequestMapping("/api/agent/overview")
@RequireLogin
@Validated
@RequiredArgsConstructor
public class AgentOverviewController {

    private final AgentOverviewService agentOverviewService;

    @Operation(summary = "查询空间 Agent 能力统计")
    @GetMapping("/stats")
    public Result<AgentOverviewStatsVO> stats(@RequestParam Long spaceId) {
        return Result.ok(agentOverviewService.getStats(spaceId));
    }
}
