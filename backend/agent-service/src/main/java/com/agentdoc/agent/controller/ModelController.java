package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.ModelCreateDTO;
import com.agentdoc.agent.pojo.vo.ModelVO;
import com.agentdoc.agent.service.ModelService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "模型管理", description = "Agent 模型与供应商配置")
@RestController
@RequestMapping("/api/agent/models")
@RequireLogin
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    @Operation(summary = "查询模型")
    @GetMapping
    public Result<List<ModelVO>> list(@RequestParam(defaultValue = "false") Boolean enabledOnly) {
        return Result.ok(modelService.list(enabledOnly));
    }

    @Operation(summary = "创建模型")
    @PostMapping
    public Result<ModelVO> create(@Valid @RequestBody ModelCreateDTO dto) {
        return Result.ok(modelService.create(dto));
    }

    @Operation(summary = "启用或禁用模型")
    @PutMapping("/{id}/status")
    public Result<ModelVO> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return Result.ok(modelService.updateStatus(id, status));
    }
}
