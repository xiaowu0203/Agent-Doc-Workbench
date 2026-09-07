package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.AgentBatchQueryDTO;
import com.agentdoc.common.feign.dto.AgentTaskOptionQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.feign.vo.AgentTaskOptionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent/internal/agents")
public class InternalAgentController {

    private final AgentService agentService;

    public InternalAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/{agentId}/execution-profile")
    public Result<AgentExecutionProfileVO> executionProfile(@PathVariable Long agentId) {
        return Result.ok(agentService.executionProfile(agentId));
    }

    @PostMapping("/refs/query")
    public Result<List<AgentRefVO>> queryRefs(@RequestBody AgentBatchQueryDTO request) {
        return Result.ok(agentService.listRefs(request.agentIds()));
    }

    @PostMapping("/task-options/query")
    public Result<List<AgentTaskOptionVO>> queryTaskOptions(@RequestBody AgentTaskOptionQueryDTO request) {
        return Result.ok(agentService.listTaskOptions(request.spaceId(), request.documentId()));
    }
}
