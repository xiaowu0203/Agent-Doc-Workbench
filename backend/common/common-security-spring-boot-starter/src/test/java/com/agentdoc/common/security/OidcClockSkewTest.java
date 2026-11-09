package com.agentdoc.common.security;

import com.agentdoc.common.constant.JwtConstant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OidcClockSkewTest {
    @Test
    void acceptsWithinThirtySecondsAndRejectsBeyondForExpiryAndNotBefore() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertThat(JwtConstant.OIDC_CLOCK_SKEW_SECONDS).isEqualTo(30);
        var validator = new JwtTimestampValidator(Duration.ofSeconds(JwtConstant.OIDC_CLOCK_SKEW_SECONDS));
        validator.setClock(Clock.fixed(now, ZoneOffset.UTC));

        assertThat(validator.validate(jwt(now, now.minusSeconds(29), now.minusSeconds(60))).hasErrors()).isFalse();
        assertThat(validator.validate(jwt(now, now.minusSeconds(31), now.minusSeconds(60))).hasErrors()).isTrue();
        assertThat(validator.validate(jwt(now, now.plusSeconds(60), now.plusSeconds(29))).hasErrors()).isFalse();
        assertThat(validator.validate(jwt(now, now.plusSeconds(60), now.plusSeconds(31))).hasErrors()).isTrue();
    }

    private Jwt jwt(Instant now, Instant expiresAt, Instant notBefore) {
        return Jwt.withTokenValue("fixture").header("alg", "none").subject("fixture")
                .issuedAt(now.minusSeconds(120)).expiresAt(expiresAt).notBefore(notBefore).build();
    }
}
