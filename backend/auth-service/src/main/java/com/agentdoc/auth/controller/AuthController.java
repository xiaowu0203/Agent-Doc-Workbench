package com.agentdoc.auth.controller;

import com.agentdoc.auth.pojo.dto.LoginRequestDTO;
import com.agentdoc.auth.pojo.dto.RefreshRequestDTO;
import com.agentdoc.auth.pojo.dto.RegisterRequestDTO;
import com.agentdoc.auth.pojo.vo.AuthResponseVO;
import com.agentdoc.auth.pojo.vo.UserVO;
import com.agentdoc.auth.service.AuthService;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册、登录、刷新、登出、当前用户。
 */
@Tag(name = "认证", description = "注册、登录、刷新令牌、当前用户")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
    public Result<UserVO> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = Long.valueOf(jwt.getSubject());
        UserVO user = authService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return Result.ok(user);
    }
}
