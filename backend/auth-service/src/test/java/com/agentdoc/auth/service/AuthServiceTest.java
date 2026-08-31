package com.agentdoc.auth.service;

import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.pojo.dto.RegisterRequestDTO;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.pojo.vo.AuthResponseVO;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserMapper userMapper;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private PlatformRoleService platformRoleService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        platformRoleService = mock(PlatformRoleService.class);
        // issueTokens 会读取 props().accessTtl()，此处 stub 掉避免 mock 返回 null 导致 NPE
        when(jwtService.props()).thenReturn(
                new JwtProperties("", "", Duration.ofMinutes(30), Duration.ofDays(7), "test"));
        authService = new AuthService(userMapper, new BCryptPasswordEncoder(), jwtService,
                refreshTokenService, platformRoleService);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        RegisterRequestDTO request = new RegisterRequestDTO("alice", "secret1", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void loginReturnsTokensOnValidCredentials() {
        String rawPassword = "secret1";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setStatus(UserStatus.ENABLED.getCode());

        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(platformRoleService.listRoleKeys(user.getId())).thenReturn(java.util.List.of());
        when(jwtService.createAccessToken(user, java.util.List.of())).thenReturn("access-token");
        when(jwtService.createRefreshToken()).thenReturn("refresh-token");

        AuthResponseVO response = authService.login("alice", rawPassword);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("alice", response.user().username());
    }

    @Test
    void loginRejectsWrongPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setUsername("alice");
        user.setPasswordHash(encoder.encode("secret1"));
        user.setStatus(UserStatus.ENABLED.getCode());

        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login("alice", "wrongpass"));
        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), ex.getCode());
    }

    @Test
    void loginRejectsDisabledUser() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setUsername("alice");
        user.setPasswordHash(encoder.encode("secret1"));
        user.setStatus(UserStatus.DISABLED.getCode());

        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login("alice", "secret1"));
        assertEquals(ErrorCode.USER_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void refreshRejectsEmptyToken() {
        when(refreshTokenService.validateAndGetUserId("bad")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh("bad"));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID.getCode(), ex.getCode());
    }
}
