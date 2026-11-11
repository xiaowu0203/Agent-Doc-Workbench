package com.agentdoc.common.utils;

import com.agentdoc.common.constant.IdentityTokenConstant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalIdentityTokenUtilsTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final String CLIENT = "task-service";

    @Test
    void humanRequiresCanonicalUserSessionAndSignedAuthenticationVersion() {
        assertThat(LocalIdentityTokenUtils.requireHuman(human().build(), NOW).getSubject()).isEqualTo("42");
        for (Jwt invalid : List.of(human().subject("042").build(), human().subject("0").build(), human().subject(CLIENT).build(),
                human().claim("auth_version", "0").build(), human().claim("auth_version", -1).build(),
                human().claims(claims -> claims.remove("auth_version")).build(), human().claim("sid", "not-a-session").build(),
                human().claim("azp", CLIENT).build())) {
            assertThatThrownBy(() -> LocalIdentityTokenUtils.requireHuman(invalid, NOW)).isInstanceOf(BadJwtException.class);
        }
    }

    @Test
    void serviceIsBoundToRegisteredClientAndCannotCarryHumanClaims() {
        assertThat(LocalIdentityTokenUtils.requireService(service().build(), NOW, Set.of(CLIENT)).getSubject()).isEqualTo(CLIENT);
        for (Jwt invalid : List.of(service().subject("42").build(), service().claim("azp", "other-service").build(),
                service().subject("workbench-web").claim("azp", "workbench-web").build(),
                service().claim("auth_version", 0L).build(), service().claim("sid", UUID.randomUUID().toString()).build(),
                service().claim("platformRoles", List.of("PLATFORM_SUPER_ADMIN")).build(), service().claim("userId", 42L).build(),
                service().claims(claims -> claims.remove("jti")).build())) {
            assertThatThrownBy(() -> LocalIdentityTokenUtils.requireService(invalid, NOW, Set.of(CLIENT))).isInstanceOf(BadJwtException.class);
        }
        assertThatThrownBy(() -> LocalIdentityTokenUtils.requireService(service().build(), NOW, Set.of())).isInstanceOf(BadJwtException.class);
    }

    @Test
    void signatureDomainPurposeAndModeCannotBeMixed() {
        for (Jwt invalid : List.of(human().header("typ", "JWT").build(), human().header("alg", "HS256").build(),
                human().header("kid", "legacy-key").build(), human().issuer("https://idp.example/realm").build(),
                human().claim("purpose", "SERVICE_PROOF").build(), human().claim("actorType", "SERVICE").build(),
                human().audience(List.of("workbench-internal")).build(), human().claims(claims -> claims.remove("aud")).build(),
                human().claim("scope", "user").build(), human().claim("scope", "workbench:access workbench:service").build())) {
            assertThatThrownBy(() -> LocalIdentityTokenUtils.requireHuman(invalid, NOW)).isInstanceOf(BadJwtException.class);
        }
        assertThatThrownBy(() -> LocalIdentityTokenUtils.requireHuman(service().build(), NOW)).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> LocalIdentityTokenUtils.requireService(human().build(), NOW, Set.of(CLIENT))).isInstanceOf(BadJwtException.class);
    }

    @Test
    void maximumLifetimesAndThirtySecondSkewAreEnforcedAtConsumption() {
        assertThatThrownBy(() -> LocalIdentityTokenUtils.requireHuman(human().expiresAt(NOW.plusSeconds(604801)).build(), NOW))
                .isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> LocalIdentityTokenUtils.requireService(service().expiresAt(NOW.plusSeconds(61)).build(), NOW, Set.of(CLIENT)))
                .isInstanceOf(BadJwtException.class);
        Jwt proof = service().build();
        assertThat(LocalIdentityTokenUtils.requireService(proof, NOW.plusSeconds(90), Set.of(CLIENT))).isSameAs(proof);
        assertThatThrownBy(() -> LocalIdentityTokenUtils.requireService(proof, NOW.plusSeconds(91), Set.of(CLIENT))).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> LocalIdentityTokenUtils.requireHuman(human().issuedAt(NOW.plusSeconds(31)).build(), NOW)).isInstanceOf(BadJwtException.class);
    }

    private Jwt.Builder human() {
        return base().subject("42").header("typ", IdentityTokenConstant.HUMAN_JOSE_TYPE).claim("purpose", "HUMAN_ACCESS")
                .audience(List.of("workbench-api")).claim("scope", " workbench:access  ").claim("azp", "workbench-web")
                .claim("actorType", "HUMAN").claim("auth_version", 0L).claim("sid", UUID.randomUUID().toString())
                .expiresAt(NOW.plusSeconds(604800));
    }
    private Jwt.Builder service() {
        return base().subject(CLIENT).header("typ", IdentityTokenConstant.SERVICE_JOSE_TYPE).claim("purpose", "SERVICE_PROOF")
                .audience(List.of("workbench-internal")).claim("scope", "workbench:service").claim("azp", CLIENT)
                .claim("actorType", "SERVICE").claim("jti", UUID.randomUUID().toString()).expiresAt(NOW.plusSeconds(60));
    }
    private Jwt.Builder base() {
        return Jwt.withTokenValue("fixture").header("alg", "RS256").header("kid", IdentityTokenConstant.LOCAL_KEY_PREFIX + "fixture")
                .issuer(IdentityTokenConstant.LOCAL_ISSUER).issuedAt(NOW);
    }
}
