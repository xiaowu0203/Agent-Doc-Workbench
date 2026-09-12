package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.param.SystemCapabilitySearchParam;
import com.agentdoc.agent.pojo.vo.SystemCapabilityCatalogVO;
import com.agentdoc.agent.pojo.vo.SystemCapabilityStatisticsVO;
import com.agentdoc.agent.service.SystemCapabilityCatalogService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

@Tag(name = "统一系统能力中心", description = "系统 Skill、Agent 模板和 MCP 模板聚合目录与平台统计")
@RestController
@RequestMapping("/api/agent/system-capabilities")
@RequireLogin
@RequiredArgsConstructor
public class SystemCapabilityCatalogController {
    private final SystemCapabilityCatalogService service;

    @Operation(summary = "统一查询系统能力目录")
    @PostMapping("/search")
    public Result<PageVO<SystemCapabilityCatalogVO>> search(
            @Valid @RequestBody SystemCapabilitySearchParam param) {
        return Result.ok(service.search(param));
    }

    @Operation(summary = "查询系统能力平台统计")
    @GetMapping("/statistics")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<SystemCapabilityStatisticsVO> statistics() {
        return Result.ok(service.statistics());
    }
}
