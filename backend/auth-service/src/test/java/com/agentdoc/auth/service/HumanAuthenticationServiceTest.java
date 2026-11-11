package com.agentdoc.auth.service;

import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HumanAuthenticationServiceTest {

    private static final long NOW = Instant.parse("2026-11-10T06:00:00Z").getEpochSecond();
    private final UserMapper users = mock(UserMapper.class);
    private final HumanAuthenticationService service = new HumanAuthenticationService(users,
            Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

    @Test
    void revokeAdvancesVersionAndIncludesMaximumClockSkewInOneWrite() {
        when(users.advanceAuthenticationVersion(42L, NOW + 30)).thenReturn(1);
        service.revoke(42L);
        verify(users).advanceAuthenticationVersion(42L, NOW + 30);
    }

    @Test
    void missingUserCannotReportSuccessfulRevocation() {
        assertThatThrownBy(() -> service.revoke(42L)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {-30, 0, 30})
    void accessIssuedBeforeRevocationIsRejectedAtEveryAcceptedClockOffset(long offset) {
        UserEntity user = revokedUser();
        assertThatThrownBy(() -> service.requireCurrentOidcAuthentication(user, token(NOW + offset, NOW + offset)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void oldRefreshCannotRecoverByAdvancingIssuedAtOrWaiting() {
        var later = new HumanAuthenticationService(users,
                Clock.fixed(Instant.ofEpochSecond(NOW + 3600), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.requireCurrentOidcAuthentication(revokedUser(), token(NOW, NOW + 3600)))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void freshAuthenticationMustBeStrictlyAfterBoundary(long offset) {
        var later = new HumanAuthenticationService(users,
                Clock.fixed(Instant.ofEpochSecond(NOW + 61), ZoneOffset.UTC));
        Jwt token = token(NOW + 30 + offset, NOW + 61);
        if (offset <= 0) {
            assertThatThrownBy(() -> later.requireCurrentOidcAuthentication(revokedUser(), token))
                    .isInstanceOf(BusinessException.class);
        } else {
            assertThatCode(() -> later.requireCurrentOidcAuthentication(revokedUser(), token)).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @MethodSource("invalidAuthenticationTimes")
    void malformedMissingAndFutureAuthenticationTimeIsRejected(Object authTime) {
        Jwt.Builder token = Jwt.withTokenValue("fixture").header("alg", "RS256")
                .issuedAt(Instant.ofEpochSecond(NOW));
        if (authTime != null) {
            token.claim("auth_time", authTime);
        }
        assertThatThrownBy(() -> service.requireCurrentOidcAuthentication(new UserEntity(), token.build()))
                .isInstanceOf(BusinessException.class);
    }

    static Stream<Object> invalidAuthenticationTimes() {
        return Stream.of(null, "123", true, 1.5, 0L, -1L, NOW + 31, Long.MAX_VALUE);
    }

    @Test
    void authenticationCannotBeFarLaterThanIssueTime() {
        assertThatThrownBy(() -> service.requireCurrentOidcAuthentication(new UserEntity(), token(NOW, NOW - 31)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.requireCurrentOidcAuthentication(new UserEntity(), token(NOW, NOW + 31)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void absentOrNegativePersistedStateIsDependencyFailure() {
        var user = new UserEntity();
        user.setOidcAuthNotBefore(null);
        assertThatThrownBy(() -> service.requireCurrentOidcAuthentication(user, token(NOW, NOW)))
                .isInstanceOf(AuthenticationServiceException.class);
        user.setOidcAuthNotBefore(-1L);
        assertThatThrownBy(() -> service.requireCurrentOidcAuthentication(user, token(NOW, NOW)))
                .isInstanceOf(AuthenticationServiceException.class);
    }

    private UserEntity revokedUser() {
        var user = new UserEntity();
        user.setAuthVersion(1L);
        user.setOidcAuthNotBefore(NOW + 30);
        return user;
    }

    private Jwt token(long authTime, long issuedAt) {
        return Jwt.withTokenValue("fixture").header("alg", "RS256").claim("auth_time", authTime)
                .issuedAt(Instant.ofEpochSecond(issuedAt)).build();
    }
}
