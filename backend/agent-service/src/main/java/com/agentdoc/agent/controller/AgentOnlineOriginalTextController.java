package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentOnlineOriginalTextService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireLogin
@RequiredArgsConstructor
public class AgentOnlineOriginalTextController {
    private final AgentOnlineOriginalTextService service;
    @Operation(summary = "按当前 Task/文档权限读取线上原始最终文本")
    @GetMapping("/api/agent/executions/tasks/{taskId}/online-original-text")
    public Result<AgentOnlineOriginalTextVO> read(@PathVariable Long taskId, @RequestParam Long spaceId) {
        return Result.ok(service.read(taskId, spaceId));
    }
}
