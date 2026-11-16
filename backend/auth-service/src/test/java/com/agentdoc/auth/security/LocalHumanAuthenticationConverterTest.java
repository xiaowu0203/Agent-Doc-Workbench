package com.agentdoc.auth.security;

import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.service.PlatformRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LocalHumanAuthenticationConverterTest {
    private final UserMapper users = mock(UserMapper.class);
    private final PlatformRoleService roles = mock(PlatformRoleService.class);
    private final LocalHumanAuthenticationConverter converter = new LocalHumanAuthenticationConverter(users, roles);

    @Test
    void readsCurrentStateVersionAndRolesInsteadOfHistoricalClaims() {
        var user = user();
        when(users.selectById(42L)).thenReturn(user);
        when(roles.listRoleKeys(42L)).thenReturn(List.of("CURRENT"));
        var resolved = converter.convert(token());
        assertThat(resolved.hasPlatformRole("CURRENT")).isTrue();
        assertThat(resolved.hasPlatformRole("HISTORICAL")).isFalse();
        user.setAuthVersion(4L);
        assertThatThrownBy(() -> converter.convert(token())).isInstanceOf(OAuth2AuthenticationException.class);
        user.setAuthVersion(3L);
        user.setStatus(UserStatus.DISABLED.getCode());
        assertThatThrownBy(() -> converter.convert(token())).isInstanceOf(OAuth2AuthenticationException.class);
        when(users.selectById(42L)).thenReturn(null);
        assertThatThrownBy(() -> converter.convert(token())).isInstanceOf(OAuth2AuthenticationException.class);
        verify(roles, times(1)).listRoleKeys(42L);
    }

    @Test
    void storageOrCorruptStateIsUnavailable() {
        var user = user(); user.setAuthVersion(null);
        when(users.selectById(42L)).thenReturn(user);
        assertThatThrownBy(() -> converter.convert(token())).isInstanceOf(AuthenticationServiceException.class);
        when(users.selectById(42L)).thenThrow(new DataAccessResourceFailureException("private connection"));
        assertThatThrownBy(() -> converter.convert(token())).isInstanceOf(AuthenticationServiceException.class)
                .hasMessage("用户认证状态不可用").hasNoCause();
        verifyNoInteractions(roles);
    }

    private UserEntity user() {
        var user = new UserEntity(); user.setId(42L); user.setStatus(UserStatus.ENABLED.getCode()); user.setAuthVersion(3L); return user;
    }

    private Jwt token() {
        return Jwt.withTokenValue("verified-by-dedicated-decoder").header("alg", "RS256").subject("42")
                .claim("auth_version", 3L).claim("platformRoles", List.of("HISTORICAL")).build();
    }
}
