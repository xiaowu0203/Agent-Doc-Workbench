package com.agentdoc.common.security;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.HumanAuthorizationException;
import com.agentdoc.common.feign.AuthIdentityFeign;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PlatformRoleVerifierTest {
    private final AuthIdentityFeign identity = mock(AuthIdentityFeign.class);
    private final Jwt jwt = Jwt.withTokenValue("verified-fixture").header("alg", "RS256").subject("42").build();

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void rawJwtOrHumanWithoutRoleCannotStartAuthorityCall() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(jwt, "", List.of()));
        assertThat(PlatformRoleVerifier.hasRole(identity, SUPER_ADMIN)).isFalse();
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(jwt, 42L, List.of()));
        assertThat(PlatformRoleVerifier.hasRole(identity, SUPER_ADMIN)).isFalse();
        verifyNoInteractions(identity);
    }

    @Test
    void separatesAuthorityResponsesAndRejectsMalformedResponses() {
        login();
        when(identity.checkPlatformRole("Bearer verified-fixture", SUPER_ADMIN))
                .thenReturn(Result.ok(), Result.fail(ErrorCode.FORBIDDEN), Result.fail(ErrorCode.UNAUTHORIZED),
                        null, Result.fail(ErrorCode.INTERNAL_ERROR), Result.fail(ErrorCode.BAD_REQUEST));
        assertThat(PlatformRoleVerifier.hasRole(identity, SUPER_ADMIN)).isTrue();
        assertThat(PlatformRoleVerifier.hasRole(identity, SUPER_ADMIN)).isFalse();
        failsWith(ErrorCode.UNAUTHORIZED);
        for (int count = 0; count < 3; count++) { failsWith(ErrorCode.SERVICE_UNAVAILABLE); }
    }

    @Test
    void classifiesAndSanitizesTransportFailuresWithoutKeepingCause() {
        login();
        when(identity.checkPlatformRole("Bearer verified-fixture", SUPER_ADMIN))
                .thenThrow(new AccessDeniedException("private"), new BadCredentialsException("private"),
                        new IllegalStateException("Bearer private credential"));
        assertThat(PlatformRoleVerifier.hasRole(identity, SUPER_ADMIN)).isFalse();
        failsWith(ErrorCode.UNAUTHORIZED);
        failsWith(ErrorCode.SERVICE_UNAVAILABLE);
    }

    private void failsWith(ErrorCode code) {
        assertThatThrownBy(() -> PlatformRoleVerifier.hasRole(identity, SUPER_ADMIN))
                .isInstanceOfSatisfying(HumanAuthorizationException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code))
                .hasMessage(code.getMessage()).hasNoCause();
    }

    private void login() {
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(jwt, 42L, List.of(SUPER_ADMIN)));
    }
}
