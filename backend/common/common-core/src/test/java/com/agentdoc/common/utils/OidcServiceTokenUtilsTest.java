package com.agentdoc.common.utils;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class OidcServiceTokenUtilsTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Set<String> CLIENTS = Set.of("task-service");

    @Test
    void classifiesRegisteredClientWithMultipleScopesWithoutMappingSubjectToUser() {
        var jwt = token(Map.of());
        assertThat(OidcServiceTokenUtils.requireService(jwt, NOW, CLIENTS)).isSameAs(jwt);
        assertThat(OidcHumanTokenUtils.isHumanAccess(jwt)).isFalse();
        assertThatThrownBy(() -> OidcServiceTokenUtils.requireService(jwt, NOW, Set.of("other-service"))).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsHumanClientMixedAudienceScopeAndActor() {
        for (var claims : List.of(Map.of("azp", "workbench-web"), Map.of("actorType", "HUMAN"),
                Map.of("aud", List.of("workbench-internal", "workbench-api")),
                Map.of("scope", "workbench:service workbench:access"), Map.of("scope", "workbench:services"),
                Map.of("typ", "ID"), Map.of("sub", ""))) {
            assertThatThrownBy(() -> OidcServiceTokenUtils.requireService(token(claims), NOW, CLIENTS)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void enforcesSixtySecondLifetimeAndNinetySecondResidual() {
        var jwt = token(Map.of());
        OidcServiceTokenUtils.requireService(jwt, NOW.plusSeconds(90), CLIENTS);
        assertThatThrownBy(() -> OidcServiceTokenUtils.requireService(jwt, NOW.plusSeconds(91), CLIENTS)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> OidcServiceTokenUtils.requireService(token(Map.of("exp", NOW.plusSeconds(61))), NOW, CLIENTS)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> OidcServiceTokenUtils.requireService(token(Map.of("iat", NOW.plusSeconds(31))), NOW, CLIENTS)).isInstanceOf(JwtException.class);
    }

    private Jwt token(Map<String, ?> overrides) {
        return Jwt.withTokenValue("verified-by-current-issuer-decoder").header("alg", "RS256").header("typ", "JWT").header("kid", "issuer-key")
                .issuer("https://identity.example/realms/workbench").subject("service-account-uuid").issuedAt(NOW).expiresAt(NOW.plusSeconds(60))
                .audience(List.of("workbench-internal")).claim("azp", "task-service").claim("typ", "Bearer")
                .claim("scope", "openid workbench:service").claims(values -> values.putAll(overrides)).build();
    }
}
