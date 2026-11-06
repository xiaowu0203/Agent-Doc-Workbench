package com.agentdoc.common.security;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

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
        when(identity.currentIdentity("Bearer fixture-token")).thenReturn(Result.ok(new IdentitySelfVO(42L, List.of("LOCAL_ROLE"))));
        var converter = new OidcRemoteHumanAuthenticationConverter(identity);
        var result = converter.convert(human().build());
        assertThat(result.getUserId()).isEqualTo(42L);
        assertThat(result.getPrincipal().getSubject()).isEqualTo("oidc-subject");
        assertThat(result.hasPlatformRole("LOCAL_ROLE")).isTrue();
        assertThat(result.hasPlatformRole("FORGED_ROLE")).isFalse();
    }

    @Test
    void rejectsServiceTokensAndDistinguishesInvalidIdentityFromUnavailableService() {
        AuthIdentityFeign identity = mock(AuthIdentityFeign.class);
        var converter = new OidcRemoteHumanAuthenticationConverter(identity);
        Jwt service = human().claim("azp", "workbench-task").build();
        assertThatThrownBy(() -> converter.convert(service)).isInstanceOf(BadCredentialsException.class);
        verify(identity, never()).currentIdentity(anyString());
        when(identity.currentIdentity(anyString())).thenReturn(Result.fail(ErrorCode.UNAUTHORIZED), Result.fail(ErrorCode.SERVICE_UNAVAILABLE));
        Jwt human = human().build();
        assertThatThrownBy(() -> converter.convert(human)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> converter.convert(human)).isInstanceOf(AuthenticationServiceException.class);
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
