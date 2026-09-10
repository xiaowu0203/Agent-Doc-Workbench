package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentExecutionQueryService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.AgentToolCallPageQueryDTO;
import com.agentdoc.common.feign.dto.AgentToolUsageQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import com.agentdoc.common.feign.vo.AgentToolCallVO;
import com.agentdoc.common.feign.vo.AgentToolUsageStatsVO;
import com.agentdoc.common.pojo.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent 执行审计", description = "查询任务执行时冻结的配置与脱敏调用轨迹")
@RestController
@RequestMapping("/api/agent/executions")
@RequireLogin
@RequiredArgsConstructor
public class AgentExecutionController {

    private final AgentExecutionQueryService queryService;

    @Operation(summary = "按工作台任务查询脱敏执行审计")
    @GetMapping("/by-task/{taskId}")
    public Result<AgentExecutionAuditVO> byTask(@PathVariable Long taskId, @RequestParam Long spaceId) {
        return Result.ok(queryService.getByWorkbenchTask(taskId, spaceId));
    }

    @Operation(summary = "分页查询工作台任务的工具调用明细")
    @PostMapping("/tool-calls/query")
    public Result<PageVO<AgentToolCallVO>> toolCalls(@RequestBody AgentToolCallPageQueryDTO request) {
        return Result.ok(queryService.getToolCalls(request));
    }

    @Operation(summary = "查询空间工具调用用量聚合")
    @PostMapping("/tool-usage/stats")
    public Result<AgentToolUsageStatsVO> toolUsageStats(@RequestBody AgentToolUsageQueryDTO request) {
        return Result.ok(queryService.getToolUsageStats(request));
    }

}
