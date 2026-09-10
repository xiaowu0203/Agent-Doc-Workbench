package com.agentdoc.auth.controller;

import com.agentdoc.auth.pojo.dto.PlatformUserCreateDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserPasswordResetDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserRoleReplaceDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserStatusUpdateDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserUpdateDTO;
import com.agentdoc.auth.pojo.param.PlatformUserSearchParam;
import com.agentdoc.auth.pojo.vo.PlatformUserStatsVO;
import com.agentdoc.auth.pojo.vo.PlatformUserVO;
import com.agentdoc.auth.service.PlatformUserService;
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

@Tag(name = "平台用户管理", description = "平台用户查询、创建、资料、状态、密码和平台管理员身份管理")
@RestController
@RequestMapping("/api/platform/users")
@RequireLogin
@PreAuthorize("@platformRoleService.hasCurrentUserRole('" + SUPER_ADMIN + "')")
@RequiredArgsConstructor
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    @Operation(summary = "分页查询平台用户")
    @PostMapping("/search")
    public Result<PageVO<PlatformUserVO>> search(@Valid @RequestBody PlatformUserSearchParam param) {
        return Result.ok(platformUserService.search(param));
    }

    @Operation(summary = "查询平台用户统计")
    @GetMapping("/stats")
    public Result<PlatformUserStatsVO> stats() {
        return Result.ok(platformUserService.stats());
    }

    @Operation(summary = "查询平台用户详情")
    @GetMapping("/{userId}")
    public Result<PlatformUserVO> detail(@PathVariable Long userId) {
        return Result.ok(platformUserService.detail(userId));
    }

    @Operation(summary = "创建平台用户")
    @PostMapping
    public Result<PlatformUserVO> create(@Valid @RequestBody PlatformUserCreateDTO dto) {
        return Result.ok(platformUserService.create(dto));
    }

    @Operation(summary = "修改平台用户资料")
    @PutMapping("/{userId}")
    public Result<PlatformUserVO> update(@PathVariable Long userId,
                                         @Valid @RequestBody PlatformUserUpdateDTO dto) {
        return Result.ok(platformUserService.update(userId, dto));
    }

    @Operation(summary = "启用或禁用平台用户")
    @PutMapping("/{userId}/status")
    public Result<PlatformUserVO> updateStatus(@PathVariable Long userId,
                                               @Valid @RequestBody PlatformUserStatusUpdateDTO dto) {
        return Result.ok(platformUserService.updateStatus(userId, dto));
    }

    @Operation(summary = "管理员重置用户密码")
    @PutMapping("/{userId}/password")
    public Result<Void> resetPassword(@PathVariable Long userId,
                                      @Valid @RequestBody PlatformUserPasswordResetDTO dto) {
        platformUserService.resetPassword(userId, dto);
        return Result.ok();
    }

    @Operation(summary = "替换用户平台角色；当前仅支持平台超级管理员")
    @PutMapping("/{userId}/platform-roles")
    public Result<PlatformUserVO> replaceRoles(@PathVariable Long userId,
                                               @Valid @RequestBody PlatformUserRoleReplaceDTO dto) {
        return Result.ok(platformUserService.replaceRoles(userId, dto));
    }
}
