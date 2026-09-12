package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.vo.McpTemplateVersionVO;
import com.agentdoc.agent.service.McpTemplateService;
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

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

@Tag(name = "系统 MCP 模板版本")
@RestController
@RequestMapping("/api/agent/mcp-template-versions")
@RequireLogin
@RequiredArgsConstructor
public class McpTemplateVersionController {
    private final McpTemplateService service;

    @Operation(summary = "发布 MCP 模板版本")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<McpTemplateVersionVO> publish(@PathVariable Long id) {
        return Result.ok(service.publish(id));
    }
}
