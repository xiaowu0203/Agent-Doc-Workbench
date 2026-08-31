package com.agentdoc.auth.controller;

import com.agentdoc.auth.pojo.dto.PlatformRoleCreateDTO;
import com.agentdoc.auth.pojo.dto.PlatformRoleUpdateDTO;
import com.agentdoc.auth.pojo.vo.PlatformRoleVO;
import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

/**
 * 平台角色管理接口。
 */
@Tag(name = "平台角色管理", description = "平台角色的查询、创建、修改和删除")
@RestController
@RequestMapping("/api/platform/roles")
@RequireLogin
@Validated
@PreAuthorize("@platformRoleService.hasCurrentUserRole('" + SUPER_ADMIN + "')")
@RequiredArgsConstructor
public class PlatformRoleController {

    private final PlatformRoleService platformRoleService;

    @Operation(summary = "查询平台角色列表")
    @GetMapping
    public Result<List<PlatformRoleVO>> list() {
        return Result.ok(platformRoleService.list().stream()
                .map(PlatformRoleVO::from)
                .toList());
    }

    @Operation(summary = "查询平台角色详情")
    @GetMapping("/{roleId}")
    public Result<PlatformRoleVO> detail(@PathVariable Long roleId) {
        return Result.ok(PlatformRoleVO.from(platformRoleService.detail(roleId)));
    }

    @Operation(summary = "创建平台角色")
    @PostMapping
    public Result<PlatformRoleVO> create(@Valid @RequestBody PlatformRoleCreateDTO dto) {
        return Result.ok(PlatformRoleVO.from(
                platformRoleService.create(dto.roleKey(), dto.displayName())));
    }

    @Operation(summary = "修改平台角色")
    @PutMapping("/{roleId}")
    public Result<PlatformRoleVO> update(@PathVariable Long roleId,
                                         @Valid @RequestBody PlatformRoleUpdateDTO dto) {
        return Result.ok(PlatformRoleVO.from(
                platformRoleService.update(roleId, dto.displayName())));
    }

    @Operation(summary = "删除平台角色")
    @DeleteMapping("/{roleId}")
    public Result<Void> delete(@PathVariable Long roleId) {
        platformRoleService.delete(roleId);
        return Result.ok();
    }
}
