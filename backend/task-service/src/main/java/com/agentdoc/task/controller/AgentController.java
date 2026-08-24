package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.task.pojo.dto.AgentCreateDTO;
import com.agentdoc.task.pojo.dto.AgentUpdateDTO;
import com.agentdoc.task.pojo.vo.AgentVO;
import com.agentdoc.task.service.AgentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Agent 管理", description = "空间 Agent 配置与启停")
@RestController
@RequestMapping("/api/task/agents")
@RequireLogin
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Operation(summary = "创建 Agent")
    @PostMapping
    public Result<AgentVO> create(@Valid @RequestBody AgentCreateDTO dto) {
        return Result.ok(agentService.create(dto));
    }

    @Operation(summary = "查询空间 Agent")
    @GetMapping
    public Result<List<AgentVO>> list(@RequestParam Long spaceId) {
        return Result.ok(agentService.list(spaceId));
    }

    @Operation(summary = "查询 Agent 详情")
    @GetMapping("/{id}")
    public Result<AgentVO> detail(@PathVariable Long id) {
        return Result.ok(agentService.detail(id));
    }

    @Operation(summary = "更新 Agent")
    @PutMapping("/{id}")
    public Result<AgentVO> update(@PathVariable Long id, @Valid @RequestBody AgentUpdateDTO dto) {
        return Result.ok(agentService.update(id, dto));
    }

    @Operation(summary = "删除 Agent")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentService.delete(id);
        return Result.ok();
    }
}
