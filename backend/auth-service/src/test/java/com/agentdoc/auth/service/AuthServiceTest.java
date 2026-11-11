package com.agentdoc.auth.service;

import com.agentdoc.common.security.ResolvedHumanAuthentication;
import com.agentdoc.common.config.WorkbenchAuthProperties;
import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.pojo.dto.ChangePasswordRequestDTO;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.AuthenticationMode;
import com.agentdoc.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthServiceTest {

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(AuthServiceTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, UserEntity.class);
    }

    private UserMapper userMapper;
    private JwtService jwtService;
    private HumanAuthenticationService humanAuthenticationService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        jwtService = mock(JwtService.class);
        humanAuthenticationService = mock(HumanAuthenticationService.class);
        authService = new AuthService(userMapper, new BCryptPasswordEncoder(), jwtService,
                humanAuthenticationService, new WorkbenchAuthProperties(), mock(PlatformAuditLogService.class));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void oidcModeCannotAuthenticateLocalPassword() {
        var properties = new WorkbenchAuthProperties();
        properties.setMode(AuthenticationMode.OIDC);
        var service = new AuthService(userMapper, new BCryptPasswordEncoder(), jwtService, humanAuthenticationService, properties, mock(PlatformAuditLogService.class));
        var error = assertThrows(BusinessException.class, () -> service.authenticateLocalUser("alice", "secret1"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(userMapper, jwtService);
    }

    @Test
    void successfulPasswordCheckDoesNotMintLegacyTokens() {
        String rawPassword = "secret1";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setStatus(UserStatus.ENABLED.getCode());

        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(userMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        UserEntity response = authService.authenticateLocalUser("alice", rawPassword);
        assertEquals("alice", response.getUsername());
        verifyNoInteractions(jwtService);
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

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.authenticateLocalUser("alice", "wrongpass"));
        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), ex.getCode());
    }

    @Test
    void loginRejectsOidcOnlyAccountWithoutCheckingLocalPassword() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthService service = new AuthService(userMapper, encoder, jwtService,
                humanAuthenticationService, new WorkbenchAuthProperties(), mock(PlatformAuditLogService.class));
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setStatus(UserStatus.ENABLED.getCode());
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.authenticateLocalUser("oidc-user", "secret1"));

        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), error.getCode());
        verify(encoder).matches(any(), any());
        verifyNoInteractions(jwtService);
    }

    @Test
    void changePasswordRejectsOidcOnlyAccountWithoutCheckingLocalPassword() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthService service = new AuthService(userMapper, encoder, jwtService,
                humanAuthenticationService, new WorkbenchAuthProperties(), mock(PlatformAuditLogService.class));
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setStatus(UserStatus.ENABLED.getCode());
        when(userMapper.lockById(10L)).thenReturn(user);
        authenticateHumanUser(10L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePassword(new ChangePasswordRequestDTO("secret1", "secret2")));

        assertEquals(ErrorCode.CURRENT_PASSWORD_INVALID.getCode(), error.getCode());
        verify(encoder, never()).matches(any(), any());
        verify(userMapper, never()).updateById(any(UserEntity.class));
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

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.authenticateLocalUser("alice", "secret1"));
        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), ex.getCode());
    }

    @Test
    void rejectsPasswordStateChangedDuringAuthentication() {
        var user = new UserEntity(); user.setId(10L); user.setStatus(UserStatus.ENABLED.getCode());
        user.setPasswordHash(new BCryptPasswordEncoder().encode("secret1"));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(userMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        var error = assertThrows(BusinessException.class, () -> authService.authenticateLocalUser("alice", "secret1"));
        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), error.getCode());
        verifyNoInteractions(jwtService);
    }

    @Test
    void changePasswordUpdatesHashAndRevokesAuthenticationVersion() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setUsername("alice");
        user.setPasswordHash(encoder.encode("secret1"));
        user.setStatus(UserStatus.ENABLED.getCode());
        when(userMapper.lockById(10L)).thenReturn(user);
        authenticateHumanUser(10L);

        authService.changePassword(new ChangePasswordRequestDTO("secret1", "secret2"));

        assertTrue(encoder.matches("secret2", user.getPasswordHash()));
        verify(userMapper).updateById(user);
        verify(humanAuthenticationService).revoke(10L);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setUsername("alice");
        user.setPasswordHash(encoder.encode("secret1"));
        user.setStatus(UserStatus.ENABLED.getCode());
        when(userMapper.lockById(10L)).thenReturn(user);
        authenticateHumanUser(10L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.changePassword(new ChangePasswordRequestDTO("wrong1", "secret2")));

        assertEquals(ErrorCode.CURRENT_PASSWORD_INVALID.getCode(), ex.getCode());
    }

    private void authenticateHumanUser(Long userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(userId))
                .claim("scope", "user")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new ResolvedHumanAuthentication(jwt, Long.valueOf(jwt.getSubject()), List.of()));
    }
}
