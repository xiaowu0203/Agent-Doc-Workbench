package com.agentdoc.auth.service;

import com.agentdoc.auth.mapper.DepartmentMapper;
import com.agentdoc.auth.mapper.PlatformRoleMapper;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.mapper.UserPlatformRoleMapper;
import com.agentdoc.auth.pojo.dto.PlatformUserCreateDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserPasswordResetDTO;
import com.agentdoc.auth.pojo.dto.ChangePasswordRequestDTO;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.pojo.entity.UserPlatformRoleEntity;
import com.agentdoc.common.config.WorkbenchAuthProperties;
import com.agentdoc.common.enums.AuthenticationMode;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformUserAuthenticationModeTest {
    private final UserMapper users = mock(UserMapper.class);
    private final UserPlatformRoleMapper bindings = mock(UserPlatformRoleMapper.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final WorkbenchAuthProperties mode = new WorkbenchAuthProperties();
    private final PlatformUserService service = new PlatformUserService(users, mock(DepartmentMapper.class), mock(PlatformRoleMapper.class),
            bindings, mock(DepartmentService.class), passwords, mock(PlatformAuditLogService.class), mock(HumanAuthenticationService.class), mode);

    @BeforeAll
    static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "mode-test");
        assistant.setCurrentNamespace(PlatformUserAuthenticationModeTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, UserEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserPlatformRoleEntity.class);
    }

    @Test
    void localCreationRequiresExplicitPasswordWithinBcryptByteLimit() {
        for (String password : List.of("", "short", "密".repeat(25))) {
            rejected(ErrorCode.BAD_REQUEST, () -> service.create(request(password)));
        }
        rejected(ErrorCode.BAD_REQUEST, () -> service.create(request(null)));
        verifyNoInteractions(users, passwords);
    }

    @Test
    void oidcDoesNotAcceptPasswordOnCreationOrReset() {
        mode.setMode(AuthenticationMode.OIDC);
        rejected(ErrorCode.BAD_REQUEST, () -> service.create(request("explicit-secret1")));
        rejected(ErrorCode.FORBIDDEN, () -> service.resetPassword(42L, new PlatformUserPasswordResetDTO("explicit-secret1")));
        verifyNoInteractions(users, passwords);
    }

    @Test
    void oidcProfilePersistsNullHashWithoutInvokingPasswordEncoder() {
        mode.setMode(AuthenticationMode.OIDC);
        var stored = new UserEntity(); stored.setId(42L); stored.setUsername("profile"); stored.setStatus(1);
        when(users.insert(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0); assertThat(user.getPasswordHash()).isNull(); user.setId(42L); return 1;
        });
        when(users.selectById(42L)).thenReturn(stored);
        when(bindings.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThat(service.create(request(null)).id()).isEqualTo(42L);
        verifyNoInteractions(passwords);
    }

    @Test
    void passwordRequestDiagnosticsDoNotExposePasswords() {
        String secret = "private-password-fixture";
        for (Object request : List.of(request(secret), new PlatformUserPasswordResetDTO(secret), new ChangePasswordRequestDTO(secret, secret))) {
            assertThat(request.toString().contains(secret)).isFalse();
        }
    }

    private PlatformUserCreateDTO request(String password) { return new PlatformUserCreateDTO("profile", password, null, null, null, null, 1, false); }
    private void rejected(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(expected.getCode()));
    }
}
