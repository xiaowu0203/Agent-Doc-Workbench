package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentExecutionQueryService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.AgentExecutionTokenUsageBatchQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageBatchVO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Agent 执行内部查询接口。 */
@Tag(name = "Agent 执行内部接口")
@RestController
@RequestMapping("/api/agent/internal/executions")
@RequiredArgsConstructor
public class AgentInternalExecutionController {

    private final AgentExecutionQueryService queryService;

    @Operation(summary = "查询执行 Token 用量")
    @GetMapping("/by-task/{taskId}/token-usage")
    public Result<AgentExecutionTokenUsageVO> tokenUsage(@PathVariable Long taskId) {
        return Result.ok(queryService.getTokenUsageByWorkbenchTask(taskId));
    }

    @Operation(summary = "批量查询执行 Token 用量")
    @PostMapping("/by-task/token-usage/query")
    public Result<List<AgentExecutionTokenUsageBatchVO>> tokenUsages(
            @RequestBody AgentExecutionTokenUsageBatchQueryDTO request) {
        return Result.ok(queryService.getTokenUsagesByWorkbenchTasks(request));
    }
}
