package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.vo.AgentTemplateVersionVO;
import com.agentdoc.agent.service.AgentTemplateService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统 Agent 模板版本")
@RestController
@RequestMapping("/api/agent/agent-template-versions")
@RequireLogin
@RequiredArgsConstructor
public class AgentTemplateVersionController {
    private final AgentTemplateService service;

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "发布 Agent 模板版本")
    public Result<AgentTemplateVersionVO> publish(@PathVariable Long id) {
        return Result.ok(service.publish(id));
    }
}
