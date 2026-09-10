package com.agentdoc.auth.controller;

import com.agentdoc.auth.config.AuthCookieProperties;
import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.auth.pojo.dto.ChangePasswordRequestDTO;
import com.agentdoc.auth.pojo.dto.LoginRequestDTO;
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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.agentdoc.auth.constant.AuthConstant.REFRESH_TOKEN_COOKIE_NAME;
import static com.agentdoc.auth.constant.AuthConstant.REFRESH_TOKEN_COOKIE_PATH;
import static com.agentdoc.auth.constant.AuthConstant.REFRESH_TOKEN_PERSISTENCE_COOKIE_NAME;

/**
 * 认证接口：注册、登录、刷新、登出、修改密码、当前用户。
 */
@Tag(name = "认证", description = "注册、登录、刷新令牌、登出、修改密码、当前用户")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PlatformRoleService platformRoleService;
    private final JwtProperties jwtProperties;
    private final AuthCookieProperties cookieProperties;

    public AuthController(AuthService authService, PlatformRoleService platformRoleService,
                          JwtProperties jwtProperties, AuthCookieProperties cookieProperties) {
        this.authService = authService;
        this.platformRoleService = platformRoleService;
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return Result.ok(authService.register(request));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<AuthResponseVO> login(@Valid @RequestBody LoginRequestDTO request, HttpServletResponse response) {
        AuthResponseVO session = authService.login(request.username(), request.password());
        writeRefreshTokenCookie(response, session.refreshToken(), request.shouldRemember());
        return Result.ok(session);
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<AuthResponseVO> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, defaultValue = "") String refreshToken,
            @CookieValue(name = REFRESH_TOKEN_PERSISTENCE_COOKIE_NAME, defaultValue = "false") boolean persistent,
            HttpServletResponse response) {
        AuthResponseVO session = authService.refresh(refreshToken);
        writeRefreshTokenCookie(response, session.refreshToken(), persistent);
        return Result.ok(session);
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, defaultValue = "") String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        clearRefreshTokenCookie(response);
        return Result.ok();
    }

    private void writeRefreshTokenCookie(HttpServletResponse response, String refreshToken, boolean persistent) {
        ResponseCookie.ResponseCookieBuilder tokenBuilder = cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        ResponseCookie.ResponseCookieBuilder persistenceBuilder = cookie(
                REFRESH_TOKEN_PERSISTENCE_COOKIE_NAME, Boolean.toString(persistent));
        if (persistent) {
            tokenBuilder.maxAge(jwtProperties.refreshTtl());
            persistenceBuilder.maxAge(jwtProperties.refreshTtl());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, tokenBuilder.build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, persistenceBuilder.build().toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_TOKEN_COOKIE_NAME, "").maxAge(0).build().toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(REFRESH_TOKEN_PERSISTENCE_COOKIE_NAME, "").maxAge(0).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(REFRESH_TOKEN_COOKIE_PATH);
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDTO request) {
        authService.changePassword(request);
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
