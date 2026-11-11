package com.agentdoc.common.security;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.AuthenticationMode;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcRemoteHumanAuthenticationConverterTest {
    @Test
    void passesOnlyVerifiedHumanTokenAndUsesAuthProjection() {
        AuthIdentityFeign identity = mock(AuthIdentityFeign.class);
        when(identity.currentIdentity("Bearer fixture-token")).thenReturn(Result.ok(new IdentitySelfVO(42L, List.of("LOCAL_ROLE"), 3L)));
        var converter = new RemoteHumanAuthenticationConverter(identity, AuthenticationMode.OIDC);
        var result = converter.convert(human().build());
        assertThat(result.getUserId()).isEqualTo(42L);
        assertThat(result.getPrincipal().getSubject()).isEqualTo("oidc-subject");
        assertThat(result.hasPlatformRole("LOCAL_ROLE")).isTrue();
        assertThat(result.hasPlatformRole("FORGED_ROLE")).isFalse();
    }

    @Test
    void rejectsServiceTokensAndDistinguishesInvalidIdentityFromUnavailableService() {
        AuthIdentityFeign identity = mock(AuthIdentityFeign.class);
        var converter = new RemoteHumanAuthenticationConverter(identity, AuthenticationMode.OIDC);
        Jwt service = human().claim("azp", "workbench-task").build();
        assertThatThrownBy(() -> converter.convert(service)).isInstanceOf(BadCredentialsException.class);
        verify(identity, never()).currentIdentity(anyString());
        when(identity.currentIdentity(anyString())).thenReturn(Result.fail(ErrorCode.UNAUTHORIZED), Result.fail(ErrorCode.SERVICE_UNAVAILABLE));
        Jwt human = human().build();
        assertThatThrownBy(() -> converter.convert(human)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> converter.convert(human)).isInstanceOf(AuthenticationServiceException.class);
    }

    @Test
    void missingOrNegativeAuthenticationVersionIsADependencyContractFailure() {
        AuthIdentityFeign identity = mock(AuthIdentityFeign.class);
        when(identity.currentIdentity(anyString())).thenReturn(
                Result.ok(new IdentitySelfVO(42L, List.of(), null)),
                Result.ok(new IdentitySelfVO(42L, List.of(), -1L)));
        var converter = new RemoteHumanAuthenticationConverter(identity, AuthenticationMode.OIDC);
        assertThatThrownBy(() -> converter.convert(human().build())).isInstanceOf(AuthenticationServiceException.class);
        assertThatThrownBy(() -> converter.convert(human().build())).isInstanceOf(AuthenticationServiceException.class);
    }

    @Test
    void numericUuidAndUnicodeSubjectsRemainOpaque() {
        var identity = mock(AuthIdentityFeign.class);
        when(identity.currentIdentity(anyString())).thenReturn(Result.ok(new IdentitySelfVO(42L, List.of(), 3L)));
        var converter = new RemoteHumanAuthenticationConverter(identity, AuthenticationMode.OIDC);
        for (String subject : List.of("123", "550e8400-e29b-41d4-a716-446655440000", "外部身份😀")) {
            var mapped = converter.convert(human().subject(subject).build());
            assertThat(mapped.getUserId()).isEqualTo(42L);
            assertThat(mapped.getPrincipal().getSubject()).isEqualTo(subject);
        }
    }

    @Test
    void realmAndClientAdminClaimsCannotGrantPlatformSuperAdmin() {
        var identity = mock(AuthIdentityFeign.class);
        when(identity.currentIdentity(anyString())).thenReturn(Result.ok(new IdentitySelfVO(42L, List.of(), 3L)));
        var jwt = human().claim("realm_access", Map.of("roles", List.of("realm-admin", "PLATFORM_SUPER_ADMIN")))
                .claim("resource_access", Map.of("workbench-api", Map.of("roles", List.of("PLATFORM_SUPER_ADMIN"))))
                .claim("platformRoles", List.of("PLATFORM_SUPER_ADMIN")).build();
        assertThat(new RemoteHumanAuthenticationConverter(identity, AuthenticationMode.OIDC).convert(jwt)
                .hasPlatformRole("PLATFORM_SUPER_ADMIN")).isFalse();
    }

    private Jwt.Builder human() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("fixture-token").header("alg", "RS256").subject("oidc-subject")
                .issuer("https://id.example/realm").audience(List.of("workbench-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("azp", "workbench-web")
                .claim("typ", "Bearer").claim("scope", "openid workbench:access")
                .claim("platformRoles", List.of("FORGED_ROLE"));
    }
}
