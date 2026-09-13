package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.AgentTemplateVersionCreateDTO;
import com.agentdoc.agent.pojo.vo.AgentTemplateVersionVO;
import com.agentdoc.agent.service.AgentTemplateService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

@Tag(name = "系统 Agent 模板版本")
@RestController
@RequestMapping("/api/agent/agent-template-versions")
@RequireLogin
@RequiredArgsConstructor
public class AgentTemplateVersionController {
    private final AgentTemplateService service;

    @PostMapping("/{id}/publish")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    @Operation(summary = "发布 Agent 模板版本")
    public Result<AgentTemplateVersionVO> publish(@PathVariable Long id) {
        return Result.ok(service.publish(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    @Operation(summary = "更新 Agent 模板草稿版本")
    public Result<AgentTemplateVersionVO> update(@PathVariable Long id,
                                                  @Valid @RequestBody AgentTemplateVersionCreateDTO dto) {
        return Result.ok(service.updateVersion(id, dto));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    @Operation(summary = "停用 Agent 模板版本")
    public Result<AgentTemplateVersionVO> disable(@PathVariable Long id) {
        return Result.ok(service.disableVersion(id));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    @Operation(summary = "恢复 Agent 模板版本")
    public Result<AgentTemplateVersionVO> enable(@PathVariable Long id) {
        return Result.ok(service.enableVersion(id));
    }
}
