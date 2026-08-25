package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
