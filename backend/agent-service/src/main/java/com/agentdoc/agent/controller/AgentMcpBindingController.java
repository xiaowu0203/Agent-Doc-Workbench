package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.AgentMcpBindingReplaceDTO;
import com.agentdoc.agent.pojo.vo.AgentMcpBindingVO;
import com.agentdoc.agent.service.AgentMcpBindingService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Agent MCP 绑定", description = "Agent 当前 MCP 绑定")
@RestController
@RequestMapping("/api/agent/agents")
@RequireLogin
@RequiredArgsConstructor
public class AgentMcpBindingController {
    private final AgentMcpBindingService service;

    @Operation(summary = "查询 Agent MCP 绑定")
    @GetMapping("/{agentId}/mcp-bindings")
    public Result<List<AgentMcpBindingVO>> list(@PathVariable Long agentId) {
        return Result.ok(service.list(agentId));
    }

    @Operation(summary = "更新 Agent MCP 绑定")
    @PutMapping("/{agentId}/mcp-bindings")
    public Result<List<AgentMcpBindingVO>> replace(@PathVariable Long agentId,
            @Valid @RequestBody AgentMcpBindingReplaceDTO dto) {
        return Result.ok(service.replace(agentId, dto));
    }
}
