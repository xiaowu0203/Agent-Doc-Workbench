package com.agentdoc.document.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.document.pojo.dto.RolePermissionReplaceDTO;
import com.agentdoc.document.pojo.dto.SpaceRoleCreateDTO;
import com.agentdoc.document.pojo.dto.SpaceRoleUpdateDTO;
import com.agentdoc.document.pojo.vo.PermissionVO;
import com.agentdoc.document.pojo.vo.SpaceRoleVO;
import com.agentdoc.document.service.SpaceRoleService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "空间角色与权限", description = "权限目录、自定义角色和角色权限绑定")
@RestController
@RequestMapping("/api/document/spaces")
@RequireLogin
@Validated
@RequiredArgsConstructor
public class SpaceRoleController {

    private final SpaceRoleService spaceRoleService;

    @Operation(summary = "查询空间权限目录")
    @GetMapping("/{spaceId}/permissions")
    @PreAuthorize("@SpacePermission.hasPermission(#spaceId, '" + com.agentdoc.common.constant.SpacePermissionConstant.ROLE_READ + "')")
    public Result<List<PermissionVO>> listPermissions(@PathVariable Long spaceId) {
        return Result.ok(spaceRoleService.listPermissions());
    }

    @Operation(summary = "查询空间角色")
    @GetMapping("/{spaceId}/roles")
    @PreAuthorize("@SpacePermission.hasPermission(#spaceId, '" + com.agentdoc.common.constant.SpacePermissionConstant.ROLE_READ + "')")
    public Result<List<SpaceRoleVO>> listRoles(@PathVariable Long spaceId) {
        return Result.ok(spaceRoleService.listRoles(spaceId));
    }

    @Operation(summary = "查询空间角色详情")
    @GetMapping("/{spaceId}/roles/{roleId}")
    @PreAuthorize("@SpacePermission.hasPermission(#spaceId, '" + com.agentdoc.common.constant.SpacePermissionConstant.ROLE_READ + "')")
    public Result<SpaceRoleVO> detail(@PathVariable Long spaceId, @PathVariable Long roleId) {
        return Result.ok(spaceRoleService.detail(spaceId, roleId));
    }

    @Operation(summary = "创建自定义空间角色")
    @PostMapping("/{spaceId}/roles")
    @PreAuthorize("@SpacePermission.hasPermission(#spaceId, '" + com.agentdoc.common.constant.SpacePermissionConstant.ROLE_MANAGE + "')")
    public Result<SpaceRoleVO> create(@PathVariable Long spaceId,
                                      @Valid @RequestBody SpaceRoleCreateDTO dto) {
        return Result.ok(spaceRoleService.create(spaceId, dto));
    }

    @Operation(summary = "修改空间角色（OWNER 受保护）")
    @PutMapping("/{spaceId}/roles/{roleId}")
    @PreAuthorize("@SpacePermission.hasPermission(#spaceId, '" + com.agentdoc.common.constant.SpacePermissionConstant.ROLE_MANAGE + "')")
    public Result<SpaceRoleVO> update(@PathVariable Long spaceId, @PathVariable Long roleId,
                                      @Valid @RequestBody SpaceRoleUpdateDTO dto) {
        return Result.ok(spaceRoleService.update(spaceId, roleId, dto));
    }

    @Operation(summary = "整体替换空间角色权限（OWNER 受保护）")
    @PutMapping("/{spaceId}/roles/{roleId}/permissions")
    @PreAuthorize("@SpacePermission.hasPermission(#spaceId, '" + com.agentdoc.common.constant.SpacePermissionConstant.ROLE_MANAGE + "')")
    public Result<SpaceRoleVO> replacePermissions(@PathVariable Long spaceId,
                                                  @PathVariable Long roleId,
                                                  @Valid @RequestBody RolePermissionReplaceDTO dto) {
        return Result.ok(spaceRoleService.replacePermissions(spaceId, roleId, dto));
    }

    @Operation(summary = "删除空间角色（OWNER 受保护）")
    @DeleteMapping("/{spaceId}/roles/{roleId}")
    @PreAuthorize("@SpacePermission.hasPermission(#spaceId, '" + com.agentdoc.common.constant.SpacePermissionConstant.ROLE_MANAGE + "')")
    public Result<Void> delete(@PathVariable Long spaceId, @PathVariable Long roleId) {
        spaceRoleService.delete(spaceId, roleId);
        return Result.ok();
    }
}
