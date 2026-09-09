package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.AgentBatchQueryDTO;
import com.agentdoc.common.feign.dto.AgentTaskOptionQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.feign.vo.AgentTaskOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Agent 能力概览", description = "空间 Agent、Skill、MCP 能力统计")
@RestController
@RequestMapping("/api/agent/internal/agents")
public class InternalAgentController {

    private final AgentService agentService;

    public InternalAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @Operation(summary = "查询Agent执行配置档案", description = "获取指定Agent的执行profile配置信息")
    @GetMapping("/{agentId}/execution-profile")
    public Result<AgentExecutionProfileVO> executionProfile(@PathVariable Long agentId) {
        return Result.ok(agentService.executionProfile(agentId));
    }

    @Operation(summary = "批量查询Agent引用", description = "根据agentIds批量查询Agent引用元数据")
    @PostMapping("/refs/query")
    public Result<List<AgentRefVO>> queryRefs(@RequestBody AgentBatchQueryDTO request) {
        return Result.ok(agentService.listRefs(request.agentIds()));
    }

    @Operation(summary = "查询Agent任务选项", description = "根据空间、文档上下文获取Agent可用任务选项")
    @PostMapping("/task-options/query")
    public Result<List<AgentTaskOptionVO>> queryTaskOptions(@RequestBody AgentTaskOptionQueryDTO request) {
        return Result.ok(agentService.listTaskOptions(request.spaceId(), request.documentId()));
    }
}
