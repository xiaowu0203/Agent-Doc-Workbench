package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.AgentTemplateCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateUpdateDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateVersionCreateDTO;
import com.agentdoc.agent.pojo.param.AgentTemplateSearchParam;
import com.agentdoc.agent.pojo.vo.AgentTemplateVO;
import com.agentdoc.agent.pojo.vo.AgentTemplateVersionVO;
import com.agentdoc.agent.service.AgentTemplateService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "系统 Agent 模板", description = "平台 Agent 模板与不可变版本")
@RestController
@RequestMapping("/api/agent/agent-templates")
@RequireLogin
@RequiredArgsConstructor
public class AgentTemplateController {
    private final AgentTemplateService service;

    @PostMapping("/search")
    @Operation(summary = "查询系统 Agent 模板")
    public Result<PageVO<AgentTemplateVO>> search(@Valid @RequestBody AgentTemplateSearchParam param) {
        return Result.ok(service.search(param));
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "创建系统 Agent 模板")
    public Result<AgentTemplateVO> create(@Valid @RequestBody AgentTemplateCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "更新系统 Agent 模板")
    public Result<AgentTemplateVO> update(@PathVariable Long id, @Valid @RequestBody AgentTemplateUpdateDTO dto) {
        return Result.ok(service.update(id, dto));
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "查询 Agent 模板版本")
    public Result<List<AgentTemplateVersionVO>> versions(@PathVariable Long id) {
        return Result.ok(service.listVersions(id));
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "创建 Agent 模板草稿版本")
    public Result<AgentTemplateVersionVO> createVersion(@PathVariable Long id,
                                                        @Valid @RequestBody AgentTemplateVersionCreateDTO dto) {
        return Result.ok(service.createVersion(id, dto));
    }
}
