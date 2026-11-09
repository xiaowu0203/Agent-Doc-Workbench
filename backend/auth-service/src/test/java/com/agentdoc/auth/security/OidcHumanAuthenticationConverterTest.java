package com.agentdoc.auth.security;

import com.agentdoc.auth.service.IdentityBindingService;
import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import com.agentdoc.common.constant.JwtConstant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OidcHumanAuthenticationConverterTest {
    private static final String ISSUER = "https://id.example/realm";
    private static final String SUBJECT = "550e8400-e29b-41d4-a716-446655440000";
    @Mock private IdentityBindingService bindings;
    @Mock private PlatformRoleService roles;

    @Test
    void timestampSkewHasExplicitThirtySecondBound() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        var validator = new JwtTimestampValidator(Duration.ofSeconds(JwtConstant.OIDC_CLOCK_SKEW_SECONDS));
        validator.setClock(Clock.fixed(now, ZoneOffset.UTC));
        var within = valid().issuedAt(now.minusSeconds(300)).expiresAt(now.minusSeconds(29)).build();
        var beyond = valid().issuedAt(now.minusSeconds(300)).expiresAt(now.minusSeconds(31)).build();
        var future = valid().issuedAt(now).expiresAt(now.plusSeconds(300)).notBefore(now.plusSeconds(31)).build();
        assertThat(validator.validate(within).hasErrors()).isFalse();
        assertThat(validator.validate(beyond).hasErrors()).isTrue();
        assertThat(validator.validate(future).hasErrors()).isTrue();
    }

    @Test
    void projectsBoundUserAndLocalRolesWithoutChangingSubject() {
        when(bindings.resolve(ISSUER, SUBJECT)).thenReturn(42L);
        when(roles.listRoleKeys(42L)).thenReturn(List.of("LOCAL_ROLE"));
        var token = valid().claim("platformRoles", List.of("FORGED_ROLE")).build();
        var authentication = new OidcHumanAuthenticationConverter(bindings, roles).convert(token);
        assertThat(authentication.getPrincipal().getSubject()).isEqualTo(SUBJECT);
        assertThat(authentication.getUserId()).isEqualTo(42L);
        assertThat(authentication.hasPlatformRole("LOCAL_ROLE")).isTrue();
        assertThat(authentication.hasPlatformRole("FORGED_ROLE")).isFalse();
    }

    @Test
    void rejectsWrongAudienceClientScopeTypeActorAndUnboundIdentity() {
        var converter = new OidcHumanAuthenticationConverter(bindings, roles);
        var invalid = List.of(
                valid().audience(List.of("workbench-web")).build(),
                valid().claim("azp", "workbench-task").build(),
                valid().claim("scope", "openid").build(),
                valid().claim("typ", "ID").build(),
                valid().claim("actorType", "SERVICE").build(),
                valid().claim("actorType", "AGENT").build(),
                valid().claim("actorType", "UNKNOWN").build(),
                valid().claims(claims -> claims.remove("exp")).build());
        for (Jwt token : invalid) {
            assertThatThrownBy(() -> converter.convert(token)).isInstanceOf(OAuth2AuthenticationException.class);
        }
        when(bindings.resolve(ISSUER, SUBJECT)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> converter.convert(valid().build())).isInstanceOf(OAuth2AuthenticationException.class);
    }

    private Jwt.Builder valid() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("fixture").header("alg", "RS256").issuer(ISSUER).subject(SUBJECT)
                .audience(List.of("workbench-api")).issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("azp", "workbench-web").claim("typ", "Bearer").claim("scope", "openid workbench:access");
    }
}
