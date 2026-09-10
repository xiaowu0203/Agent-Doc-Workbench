package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.McpTemplateCreateDTO;
import com.agentdoc.agent.pojo.dto.McpTemplateUpdateDTO;
import com.agentdoc.agent.pojo.param.McpTemplateSearchParam;
import com.agentdoc.agent.pojo.vo.McpTemplateVO;
import com.agentdoc.agent.service.McpTemplateService;
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

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

@Tag(name = "系统 MCP 模板", description = "无空间凭证的系统 MCP 连接模板")
@RestController
@RequestMapping("/api/agent/mcp-templates")
@RequireLogin
@RequiredArgsConstructor
public class McpTemplateController {
    private final McpTemplateService service;

    @Operation(summary = "查询系统 MCP 模板")
    @PostMapping("/search")
    public Result<PageVO<McpTemplateVO>> search(@Valid @RequestBody McpTemplateSearchParam param) {
        return Result.ok(service.search(param));
    }

    @Operation(summary = "查询系统 MCP 模板详情")
    @GetMapping("/{id}")
    public Result<McpTemplateVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @Operation(summary = "创建系统 MCP 模板")
    @PostMapping
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<McpTemplateVO> create(@Valid @RequestBody McpTemplateCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "更新系统 MCP 模板")
    @PutMapping("/{id}")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<McpTemplateVO> update(@PathVariable Long id,
                                        @Valid @RequestBody McpTemplateUpdateDTO dto) {
        return Result.ok(service.update(id, dto));
    }
}
