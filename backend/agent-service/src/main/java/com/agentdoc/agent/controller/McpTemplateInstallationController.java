package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.McpTemplateInstallDTO;
import com.agentdoc.agent.pojo.vo.McpServerVO;
import com.agentdoc.agent.service.McpTemplateService;
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

@Tag(name = "空间 MCP 模板安装", description = "将系统 MCP 模板安装为空间独立连接")
@RestController
@RequestMapping("/api/agent/spaces/{spaceId}/mcp-installations")
@RequireLogin
@RequiredArgsConstructor
public class McpTemplateInstallationController {
    private final McpTemplateService service;

    @Operation(summary = "安装系统 MCP 模板并测试连接")
    @PostMapping
    public Result<McpServerVO> install(@PathVariable Long spaceId,
                                       @Valid @RequestBody McpTemplateInstallDTO dto) {
        return Result.ok(service.install(spaceId, dto));
    }
}
