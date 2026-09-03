package com.agentdoc.agent.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.AuthFeign;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static com.agentdoc.common.constant.JwtConstant.CLAIM_PLATFORM_ROLES;
import static com.agentdoc.common.constant.JwtConstant.CLAIM_SCOPE;
import static com.agentdoc.common.constant.JwtConstant.SCOPE_USER;
import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformAccessServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void confirmsSuperAdminRoleWithAuthService() {
        AuthFeign authFeign = mock(AuthFeign.class);
        PlatformAccessService service = new PlatformAccessService(authFeign);
        loginWithRoles(List.of(SUPER_ADMIN));
        when(authFeign.checkPlatformRole(SUPER_ADMIN)).thenReturn(Result.ok());

        assertThat(service.hasRole(SUPER_ADMIN)).isTrue();
        verify(authFeign).checkPlatformRole(SUPER_ADMIN);
    }

    @Test
    void rejectsBeforeRemoteCallWhenJwtHasNoRole() {
        AuthFeign authFeign = mock(AuthFeign.class);
        PlatformAccessService service = new PlatformAccessService(authFeign);
        loginWithRoles(List.of());

        assertThat(service.hasRole(SUPER_ADMIN)).isFalse();
        verify(authFeign, never()).checkPlatformRole(SUPER_ADMIN);
    }

    private void loginWithRoles(List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("1001")
                .claim(CLAIM_SCOPE, SCOPE_USER)
                .claim(CLAIM_PLATFORM_ROLES, roles)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
