package com.agentdoc.auth.controller;

import com.agentdoc.auth.config.AuthCookieProperties;
import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.auth.pojo.dto.LoginRequestDTO;
import com.agentdoc.auth.pojo.vo.AuthResponseVO;
import com.agentdoc.auth.pojo.vo.UserVO;
import com.agentdoc.auth.service.AuthService;
import com.agentdoc.auth.service.PlatformRoleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private AuthService authService;
    private HttpServletResponse response;
    private AuthController controller;
    private AuthResponseVO session;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        response = mock(HttpServletResponse.class);
        JwtProperties jwtProperties = new JwtProperties(
                "", "", Duration.ofMinutes(30), Duration.ofDays(7), "test");
        controller = new AuthController(authService, mock(PlatformRoleService.class), jwtProperties,
                new AuthCookieProperties(false, "Strict"));
        session = AuthResponseVO.of("access-token", "refresh-token", 1800,
                new UserVO(1L, "alice", "Alice", null, null), List.of());
    }

    @Test
    void loginWritesPersistentHttpOnlyRefreshCookieWithoutExposingTokenInJson() throws Exception {
        when(authService.login("alice", "secret1")).thenReturn(session);

        controller.login(new LoginRequestDTO("alice", "secret1", true), response);

        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), argThat(value ->
                value.startsWith("adw_refresh_token=refresh-token; Path=/api/auth; Max-Age=604800;")
                        && value.endsWith("HttpOnly; SameSite=Strict")));
        String json = new ObjectMapper().writeValueAsString(session);
        assertFalse(json.contains("refresh-token"));
        assertFalse(json.contains("refreshToken"));
    }

    @Test
    void productionCookieUsesSecureAndStrictSameSite() {
        JwtProperties jwtProperties = new JwtProperties(
                "", "", Duration.ofMinutes(30), Duration.ofDays(7), "test");
        controller = new AuthController(authService, mock(PlatformRoleService.class), jwtProperties,
                new AuthCookieProperties(true, "Strict"));
        when(authService.login("alice", "secret1")).thenReturn(session);

        controller.login(new LoginRequestDTO("alice", "secret1", true), response);

        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), argThat(value ->
                value.startsWith("adw_refresh_token=refresh-token; Path=/api/auth; Max-Age=604800;")
                        && value.endsWith("Secure; HttpOnly; SameSite=Strict")));
    }

    @Test
    void refreshReadsCookieAndRotatesItAsSessionCookie() {
        when(authService.refresh("old-refresh-token")).thenReturn(session);

        controller.refresh("old-refresh-token", false, response);

        verify(authService).refresh("old-refresh-token");
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), argThat(value ->
                value.equals("adw_refresh_token=refresh-token; Path=/api/auth; HttpOnly; SameSite=Strict")));
    }

    @Test
    void logoutRevokesCookieTokenAndExpiresBrowserCookie() {
        controller.logout("refresh-token", response);

        verify(authService).logout("refresh-token");
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), argThat(value ->
                value.startsWith("adw_refresh_token=; Path=/api/auth; Max-Age=0;")
                        && value.endsWith("HttpOnly; SameSite=Strict")));
    }
}
