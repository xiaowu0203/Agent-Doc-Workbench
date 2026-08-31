package com.agentdoc.auth.controller;

import com.agentdoc.auth.pojo.dto.LoginRequestDTO;
import com.agentdoc.auth.pojo.dto.RefreshRequestDTO;
import com.agentdoc.auth.pojo.dto.RegisterRequestDTO;
import com.agentdoc.auth.pojo.vo.AuthResponseVO;
import com.agentdoc.auth.pojo.vo.UserVO;
import com.agentdoc.auth.service.AuthService;
import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.UserBatchQueryDTO;
import com.agentdoc.common.feign.vo.UserRefVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口：注册、登录、刷新、登出、当前用户。
 */
@Tag(name = "认证", description = "注册、登录、刷新令牌、当前用户")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PlatformRoleService platformRoleService;

    public AuthController(AuthService authService, PlatformRoleService platformRoleService) {
        this.authService = authService;
        this.platformRoleService = platformRoleService;
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return Result.ok(authService.register(request));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<AuthResponseVO> login(@Valid @RequestBody LoginRequestDTO request) {
        return Result.ok(authService.login(request.username(), request.password()));
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<AuthResponseVO> refresh(@Valid @RequestBody RefreshRequestDTO request) {
        return Result.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody RefreshRequestDTO request) {
        authService.logout(request.refreshToken());
        return Result.ok();
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.currentUser());
    }

    @Operation(summary = "内部签发任务能力令牌")
    @PostMapping("/internal/task-capabilities")
    public Result<String> issueTaskCapability(@RequestBody TaskCapabilityIssueDTO request) {
        return Result.ok(authService.issueTaskCapability(request));
    }

    @Operation(summary = "校验当前用户是否拥有roleKey角色（远程调用，目前作用仅仅只是查看是否为平台超级管理员）")
    @GetMapping("/internal/platform-role")
    public Result<Void> checkPlatformRole(@RequestParam String roleKey) {
        platformRoleService.requireCurrentUserRole(roleKey);
        return Result.ok();
    }

    @Operation(summary = "内部批量查询用户展示信息")
    @PostMapping("/internal/users/query")
    public Result<List<UserRefVO>> queryUsers(@RequestBody UserBatchQueryDTO request) {
        return Result.ok(authService.queryUsers(request));
    }
}
