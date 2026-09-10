package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.McpServerCreateDTO;
import com.agentdoc.agent.pojo.dto.McpServerUpdateDTO;
import com.agentdoc.agent.pojo.param.McpServerSearchParam;
import com.agentdoc.agent.pojo.vo.McpServerVO;
import com.agentdoc.agent.pojo.vo.McpConnectionTestVO;
import com.agentdoc.agent.pojo.vo.McpToolVO;
import com.agentdoc.agent.service.McpServerService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "MCP管理", description = "MCP 元数据管理")
@RestController
@RequestMapping("/api/agent/mcp-servers")
@RequireLogin
@RequiredArgsConstructor
public class McpServerController {
    private final McpServerService service;

    @Operation(summary = "创建 MCP")
    @PostMapping
    public Result<McpServerVO> create(@Valid @RequestBody McpServerCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "查询 MCP 列表")
    @PostMapping("/search")
    public Result<PageVO<McpServerVO>> list(@Valid @RequestBody McpServerSearchParam param) {
        return Result.ok(service.list(param));
    }

    @Operation(summary = "查询 MCP 详情")
    @GetMapping("/{id}")
    public Result<McpServerVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @Operation(summary = "测试 MCP 连接并发现工具")
    @PostMapping("/{id}/test-connect")
    public Result<McpConnectionTestVO> testConnect(@PathVariable Long id) {
        return Result.ok(service.testConnection(id));
    }

    @Operation(summary = "查询 MCP 最近一次成功发现的工具")
    @GetMapping("/{id}/tools")
    public Result<List<McpToolVO>> tools(@PathVariable Long id) {
        return Result.ok(service.tools(id));
    }

    @Operation(summary = "更新 MCP")
    @PutMapping("/{id}")
    public Result<McpServerVO> update(@PathVariable Long id,
                                      @Valid @RequestBody McpServerUpdateDTO dto) {
        return Result.ok(service.update(id, dto));
    }

    @Operation(summary = "删除 MCP")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
