package com.agentdoc.document.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.SpaceBudgetVO;
import com.agentdoc.document.pojo.dto.SpaceCreateDTO;
import com.agentdoc.document.pojo.dto.SpaceUpdateDTO;
import com.agentdoc.document.pojo.vo.SpaceVO;
import com.agentdoc.document.pojo.vo.EffectivePermissionVO;
import com.agentdoc.document.service.SpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
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

@Tag(name = "空间管理", description = "空间 CRUD 与我的空间列表")
@RestController
@RequestMapping("/api/document/spaces")
@RequireLogin
@Validated
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @Operation(summary = "创建空间")
    @PostMapping
    public Result<SpaceVO> create(@Valid @RequestBody SpaceCreateDTO dto) {
        return Result.ok(spaceService.create(dto));
    }

    @Operation(summary = "我的空间列表")
    @GetMapping
    public Result<List<SpaceVO>> listMySpaces() {
        return Result.ok(spaceService.listMySpaces());
    }

    @Operation(summary = "空间详情")
    @GetMapping("/{id}")
    @PreAuthorize("@SpacePermission.hasPermission(#id, '" + com.agentdoc.common.constant.SpacePermissionConstant.SPACE_READ + "')")
    public Result<SpaceVO> detail(@PathVariable Long id) {
        return Result.ok(spaceService.detail(id));
    }

    @Operation(summary = "更新空间（space:manage）")
    @PutMapping("/{id}")
    @PreAuthorize("@SpacePermission.hasPermission(#id, '" + com.agentdoc.common.constant.SpacePermissionConstant.SPACE_MANAGE + "')")
    public Result<SpaceVO> update(@PathVariable Long id, @Valid @RequestBody SpaceUpdateDTO dto) {
        return Result.ok(spaceService.update(id, dto));
    }

    @Operation(summary = "校验当前用户空间权限（服务间调用）")
    @GetMapping("/{id}/permission")
    public Result<Void> checkPermission(@PathVariable Long id, @RequestParam String permissionCode) {
        spaceService.requirePermission(id, permissionCode);
        return Result.ok();
    }

    @Operation(summary = "查询当前用户在空间中的有效权限")
    @GetMapping("/{id}/me/permissions")
    @PreAuthorize("@SpacePermission.hasPermission(#id, '" + com.agentdoc.common.constant.SpacePermissionConstant.SPACE_READ + "')")
    public Result<EffectivePermissionVO> effectivePermissions(@PathVariable Long id) {
        return Result.ok(spaceService.getEffectivePermissions(id));
    }

    @Operation(summary = "查询空间 Agent 执行预算")
    @GetMapping("/{id}/execution-budget")
    @PreAuthorize("@SpacePermission.hasPermission(#id, '" + com.agentdoc.common.constant.SpacePermissionConstant.TASK_CREATE + "')")
    public Result<SpaceBudgetVO> executionBudget(@PathVariable Long id) {
        return Result.ok(spaceService.getExecutionBudget(id));
    }

    @Operation(summary = "删除空间（space:delete）：逻辑删除空间及其成员")
    @DeleteMapping("/{id}")
    @PreAuthorize("@SpacePermission.hasPermission(#id, '" + com.agentdoc.common.constant.SpacePermissionConstant.SPACE_DELETE + "')")
    public Result<Void> delete(@PathVariable Long id) {
        spaceService.delete(id);
        return Result.ok();
    }
}
