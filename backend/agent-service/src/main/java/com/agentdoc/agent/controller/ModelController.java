package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.ModelCreateDTO;
import com.agentdoc.agent.pojo.dto.ModelUpdateDTO;
import com.agentdoc.agent.pojo.param.ModelSearchParam;
import com.agentdoc.agent.pojo.vo.ModelConnectionTestVO;
import com.agentdoc.agent.pojo.vo.ModelOptionVO;
import com.agentdoc.agent.pojo.vo.ModelVO;
import com.agentdoc.agent.service.ModelService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

@Tag(name = "模型管理", description = "Agent 模型与供应商配置")
@RestController
@RequestMapping("/api/agent/models")
@RequireLogin
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    @Operation(summary = "查询模型")
    @GetMapping
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<List<ModelVO>> list(@RequestParam(defaultValue = "false") Boolean enabledOnly) {
        return Result.ok(modelService.list(enabledOnly));
    }

    @Operation(summary = "分页查询平台模型")
    @PostMapping("/search")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<PageVO<ModelVO>> search(@Valid @RequestBody ModelSearchParam param) {
        return Result.ok(modelService.search(param));
    }

    @Operation(summary = "查询 Agent 可选模型摘要")
    @GetMapping("/options")
    public Result<List<ModelOptionVO>> options(@RequestParam(defaultValue = "false") Boolean enabledOnly) {
        return Result.ok(modelService.list(enabledOnly).stream().map(ModelOptionVO::from).toList());
    }

    @Operation(summary = "创建模型")
    @PostMapping
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<ModelVO> create(@Valid @RequestBody ModelCreateDTO dto) {
        return Result.ok(modelService.create(dto));
    }

    @Operation(summary = "测试模型连通性")
    @PostMapping("/test-connect")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<ModelConnectionTestVO> testConnect(@Valid @RequestBody ModelCreateDTO dto) {
        return Result.ok(modelService.testConnect(dto));
    }

    @Operation(summary = "测试已保存模型的连通性")
    @PostMapping("/{id}/test-connect")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<ModelConnectionTestVO> testSavedConnect(@PathVariable Long id) {
        return Result.ok(modelService.testConnect(id));
    }

    @Operation(summary = "更新模型配置")
    @PutMapping("/{id}")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<ModelVO> update(@PathVariable Long id, @Valid @RequestBody ModelUpdateDTO dto) {
        return Result.ok(modelService.update(id, dto));
    }

    @Operation(summary = "启用或禁用模型")
    @PutMapping("/{id}/status")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<ModelVO> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return Result.ok(modelService.updateStatus(id, status));
    }
}
