package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.AgentTemplateInstallDTO;
import com.agentdoc.agent.pojo.vo.AgentVO;
import com.agentdoc.agent.service.AgentTemplateService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent 模板安装")
@RestController
@RequestMapping("/api/agent/spaces/{spaceId}/agent-installations")
@RequireLogin
@RequiredArgsConstructor
public class AgentTemplateInstallationController {
    private final AgentTemplateService service;

    @PostMapping
    @Operation(summary = "将系统模板安装为空间 Agent")
    public Result<AgentVO> install(@PathVariable Long spaceId,
                                   @Valid @RequestBody AgentTemplateInstallDTO dto) {
        return Result.ok(service.install(spaceId, dto));
    }
}
