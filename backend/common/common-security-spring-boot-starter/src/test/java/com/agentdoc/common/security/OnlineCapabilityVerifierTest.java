package com.agentdoc.common.security;

import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import static com.agentdoc.common.enums.OnlineCapabilityPurpose.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

class OnlineCapabilityVerifierTest {
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final SecurityVerifyProperties properties = new SecurityVerifyProperties();
    private final OnlineCapabilityVerifier verifier = new OnlineCapabilityVerifier(decoder, properties);

    @Test
    void disabledByDefaultAndNoClockSkewRevivalOrExtendedTtl() {
        var valid = jwt(builder -> { });
        when(decoder.decode("proof")).thenReturn(valid);
        assertThatThrownBy(() -> verifier.verify("proof", ONLINE_CONTROL, "11", List.of())).isInstanceOf(JwtException.class);
        properties.setOnlineCapabilityEnabled(true);
        assertThat(verifier.verify("proof", ONLINE_CONTROL, "11", List.of())).isSameAs(valid);
        for (Consumer<Jwt.Builder> mutation : List.<Consumer<Jwt.Builder>>of(
                builder -> builder.issuedAt(Instant.now().minusSeconds(10)).notBefore(Instant.now().minusSeconds(10))
                        .expiresAt(Instant.now().minusSeconds(1)),
                builder -> builder.issuedAt(Instant.now().minusSeconds(30)).expiresAt(Instant.now().plusSeconds(300)),
                builder -> builder.notBefore(Instant.now().plusSeconds(30)))) {
            when(decoder.decode("proof")).thenReturn(jwt(mutation));
            assertThatThrownBy(() -> verifier.verify("proof", ONLINE_CONTROL, "11", List.of())).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void rejectsPurposeAudienceActorSchemaAndPrivilegeMixing() {
        properties.setOnlineCapabilityEnabled(true);
        for (Consumer<Jwt.Builder> mutation : List.<Consumer<Jwt.Builder>>of(
                builder -> builder.claim("aud", List.of(ONLINE_CONTROL.audience(), ONLINE_OBSERVE.audience())),
                builder -> builder.claim(PURPOSE, ONLINE_CANCEL.name()), builder -> builder.claim(SCHEMA, "2"),
                builder -> builder.claim(SCHEMA, 1), builder -> builder.claim(JwtConstant.CLAIM_SPACE_ID, 22L),
                builder -> builder.claim(JwtConstant.CLAIM_ACTOR_TYPE, "AGENT"),
                builder -> builder.claim(JwtConstant.CLAIM_AGENT_ACTIONS, List.of("WRITE_DRAFT")),
                builder -> builder.claim(JwtConstant.CLAIM_WORKER_ACTIONS, List.of("CANCEL")),
                builder -> builder.claim(JwtConstant.CLAIM_PLATFORM_ROLES, List.of("PLATFORM_SUPER_ADMIN")),
                builder -> builder.claim(TASK_SET_HASH, "a".repeat(64)), builder -> builder.claim("iss", "other"))) {
            when(decoder.decode("proof")).thenReturn(jwt(mutation));
            assertThatThrownBy(() -> verifier.verify("proof", ONLINE_CONTROL, "11", List.of())).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void exactSetCannotBecomeOtherPurposeOrExperiment() {
        properties.setOnlineCapabilityEnabled(true);
        Jwt observe = jwt(builder -> builder.claim("aud", List.of(ONLINE_OBSERVE.audience()))
                .claim(PURPOSE, ONLINE_OBSERVE.name()).claim(TASK_SET_HASH, OnlineCapabilityUtils.taskSetHash(List.of("2", "10"))));
        when(decoder.decode("proof")).thenReturn(observe);
        assertThat(verifier.verify("proof", ONLINE_OBSERVE, "11", List.of("10", "2"))).isSameAs(observe);
        assertThatThrownBy(() -> verifier.verify("proof", ONLINE_CANCEL, "11", List.of("2", "10"))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> verifier.verify("proof", ONLINE_OBSERVE, "12", List.of("2", "10"))).isInstanceOf(JwtException.class);
        for (List<String> ids : List.of(List.of("2"), List.of("2", "10", "3"), List.of("2", "2"))) {
            assertThatThrownBy(() -> verifier.verify("proof", ONLINE_OBSERVE, "11", ids)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void taskSetSortIsNumericAndGeneralDecodersRejectEveryNarrowAudience() {
        assertThat(OnlineCapabilityUtils.taskIds(List.of("10", "2", "9223372036854775807")))
                .containsExactly("2", "10", "9223372036854775807");
        for (String audience : List.of(ONLINE_CONTROL.audience(), ONLINE_OBSERVE.audience(), ONLINE_CANCEL.audience(), WAIT_AUDIENCE)) {
            assertThatThrownBy(() -> OnlineCapabilityUtils.rejectGeneralAccess(jwt(builder -> builder.claim("aud", List.of(audience)))))
                    .isInstanceOf(JwtException.class);
        }
        var user = Jwt.withTokenValue("user").header("alg", "RS256").subject("501").claim("scope", "user").build();
        assertThat(OnlineCapabilityUtils.rejectGeneralAccess(user)).isSameAs(user);
    }

    private Jwt jwt(Consumer<Jwt.Builder> mutation) {
        Instant now = Instant.now();
        var builder = Jwt.withTokenValue("proof").header("alg", "RS256").issuer("agent-doc-workbench")
                .subject("evaluation-service").issuedAt(now.minusSeconds(1)).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(299))
                .claim("jti", "unique").claim("aud", List.of(ONLINE_CONTROL.audience()))
                .claim("actorType", "SERVICE").claim("scope", "service").claim("service", "evaluation-service")
                .claim("spaceId", "22").claim(EXPERIMENT_ID, "11").claim(MANIFEST_HASH, "a".repeat(64))
                .claim(AUTHORIZED_BY, "501").claim(SCHEMA, 2).claim(PURPOSE, ONLINE_CONTROL.name());
        mutation.accept(builder);
        return builder.build();
    }

    @Test
    void waitOnlyAllowsOriginalTaskAndNoExecutionOrGeneralScope() {
        properties.setOnlineCapabilityEnabled(true);
        Instant now = Instant.now();
        var wait = Jwt.withTokenValue("wait").header("alg", "RS256").issuer("agent-doc-workbench")
                .subject("10").issuedAt(now.minusSeconds(1)).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(299))
                .claim("jti", "wait-id").claim("aud", List.of(WAIT_AUDIENCE)).claim("actorType", "SERVICE")
                .claim("service", TASK_SERVICE).claim("scope", WAIT_SCOPE).claim("taskId", "10").claim("spaceId", "22")
                .claim(EXPERIMENT_ID, "11").claim(ASSIGNMENT_ID, "12").claim(BINDING_SCHEMA, 2)
                .claim(BINDING_HASH, "b".repeat(64)).claim(MANIFEST_HASH, "a".repeat(64)).claim(PURPOSE, WAIT_PURPOSE).build();
        when(decoder.decode("wait")).thenReturn(wait);
        assertThat(verifier.verifyWait("wait", "10")).isSameAs(wait);
        assertThatThrownBy(() -> verifier.verifyWait("wait", "11")).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> verifier.verify("wait", ONLINE_CONTROL, "11", List.of())).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> OnlineCapabilityUtils.rejectGeneralAccess(wait)).isInstanceOf(JwtException.class);
        for (Consumer<Jwt.Builder> mutation : List.<Consumer<Jwt.Builder>>of(
                builder -> builder.claim(JwtConstant.CLAIM_AGENT_ACTIONS, List.of("READ_FRAGMENT")),
                builder -> builder.claim(JwtConstant.CLAIM_WORKER_ACTIONS, List.of("CANCEL")),
                builder -> builder.claim(SLOT_GENERATION, 1), builder -> builder.claim(SLOT_PERMIT_HASH, "c".repeat(64)),
                builder -> builder.claim(BINDING_SCHEMA, "2"), builder -> builder.claim(ASSIGNMENT_ID, 12L),
                builder -> builder.claim("aud", List.of(JwtConstant.TASK_CAPABILITY_AUDIENCE)),
                builder -> builder.issuedAt(now.minusSeconds(301)).notBefore(now.minusSeconds(301)).expiresAt(now.minusSeconds(1)))) {
            var builder = Jwt.withTokenValue("bad-wait").headers(headers -> headers.putAll(wait.getHeaders()))
                    .claims(claims -> claims.putAll(wait.getClaims())); mutation.accept(builder);
            when(decoder.decode("wait")).thenReturn(builder.build());
            assertThatThrownBy(() -> verifier.verifyWait("wait", "10")).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void httpTaskSetRequiresDecimalTextAndRejectsCoercionDuplicatesOrTrailingJson() {
        assertThat(OnlineCapabilityUtils.parseTaskIds("[\"10\",\"2\",\"9223372036854775807\"]"))
                .containsExactly("2", "10", "9223372036854775807");
        for (String json : List.of("[2]", "[9223372036854775807]", "[\"2\",\"2\"]", "[\"9223372036854775808\"]",
                "[\"2\"] [\"3\"]", "{}", "[]")) {
            assertThatThrownBy(() -> OnlineCapabilityUtils.parseTaskIds(json)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
