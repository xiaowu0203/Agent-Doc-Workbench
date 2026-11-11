package com.agentdoc.common.security;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.AuthenticationMode;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LocalRemoteHumanAuthenticationConverterTest {
    private final AuthIdentityFeign identity = mock(AuthIdentityFeign.class);
    private final RemoteHumanAuthenticationConverter converter = new RemoteHumanAuthenticationConverter(identity, AuthenticationMode.LOCAL);

    @Test
    void rechecksEveryRequestAndUsesCurrentRoles() {
        when(identity.currentIdentity("Bearer verified-local"))
                .thenReturn(Result.ok(new IdentitySelfVO(42L, List.of("CURRENT"), 3L)), Result.ok(new IdentitySelfVO(42L, List.of(), 3L)));
        var token = human().build();
        assertThat(converter.convert(token).hasPlatformRole("CURRENT")).isTrue();
        assertThat(converter.convert(token).hasPlatformRole("CURRENT")).isFalse();
        verify(identity, times(2)).currentIdentity("Bearer verified-local");
    }

    @Test
    void rejectsOldVersionAndTreatsIdentityMismatchAsDependencyFailure() {
        when(identity.currentIdentity(anyString())).thenReturn(Result.ok(new IdentitySelfVO(42L, List.of(), 4L)),
                Result.ok(new IdentitySelfVO(43L, List.of(), 3L)));
        assertThatThrownBy(() -> converter.convert(human().build())).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> converter.convert(human().build())).isInstanceOf(AuthenticationServiceException.class);
    }

    @Test
    void rejectsWrongPurposeBeforeRpcAndSanitizesRpcFailures() {
        assertThatThrownBy(() -> converter.convert(human().claim("actorType", "SERVICE").build())).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(identity);
        when(identity.currentIdentity(anyString())).thenThrow(new IllegalStateException("private token and URL"));
        assertThatThrownBy(() -> converter.convert(human().build())).isInstanceOf(AuthenticationServiceException.class)
                .hasMessage("身份服务不可用").hasNoCause();
    }

    private Jwt.Builder human() {
        var now = Instant.now();
        return Jwt.withTokenValue("verified-local").header("alg", "RS256").header("typ", "adw-human+jwt").header("kid", "local-identity-fixture")
                .claim("iss", "urn:agent-doc-workbench:local-identity").subject("42").audience(List.of("workbench-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("purpose", "HUMAN_ACCESS").claim("scope", "workbench:access")
                .claim("actorType", "HUMAN").claim("azp", "workbench-web").claim("auth_version", 3L).claim("sid", UUID.randomUUID().toString());
    }
}
