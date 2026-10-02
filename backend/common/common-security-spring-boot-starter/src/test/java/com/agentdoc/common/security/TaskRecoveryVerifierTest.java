package com.agentdoc.common.security;

import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.feign.dto.TaskRecoveryIdentityDTO;
import com.agentdoc.common.utils.TaskRecoveryJwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskRecoveryVerifierTest {
    private final SecurityVerifyProperties properties = new SecurityVerifyProperties();
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final TaskRecoveryVerifier verifier = new TaskRecoveryVerifier(decoder, properties);
    private final TaskRecoveryIdentityDTO identity = new TaskRecoveryIdentityDTO(1L, 2L, 3L, 4L, "LIVE", 1L,
            "a".repeat(64), 1, "b".repeat(64), null);

    @Test
    void defaultsDisabledAndEmergencySwitchRejectsAnOtherwiseValidCapability() {
        when(decoder.decode("token")).thenReturn(token(false, claims -> { }));
        assertDenied();
        properties.setTaskRecoveryEnabled(true);
        assertThat(verifier.verify("token", TaskRecoveryConstant.QUERY, "remote").getSubject()).isEqualTo("task-service");
        properties.setTaskRecoveryEnabled(false);
        assertDenied();
    }

    @Test
    void checksEveryFrozenFieldAndRejectsGeneralAccess() {
        properties.setTaskRecoveryEnabled(true);
        Jwt jwt = token(false, claims -> { });
        when(decoder.decode("token")).thenReturn(jwt);
        verifier.requireIdentity(verifier.verify("token", TaskRecoveryConstant.QUERY, "remote"), identity);
        identity.toClaims().forEach((field, value) -> {
            Jwt changed = token(false, claims -> claims.put(field, value == null ? "c".repeat(64) : "different"));
            assertThatThrownBy(() -> verifier.requireIdentity(changed, identity)).isInstanceOf(JwtException.class);
        });
        assertThatThrownBy(() -> TaskRecoveryJwtUtils.rejectGeneralAccess(jwt)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsActorScopeIssuerAudiencePurposeAndResourceSubstitution() {
        properties.setTaskRecoveryEnabled(true);
        for (String field : List.of("sub", "iss", JwtConstant.CLAIM_ACTOR_TYPE, JwtConstant.CLAIM_SCOPE,
                JwtConstant.CLAIM_SERVICE, TaskRecoveryConstant.PURPOSE, TaskRecoveryConstant.A2A_TASK_ID)) {
            when(decoder.decode("token")).thenReturn(token(false, claims -> claims.put(field, "wrong")));
            assertDenied();
        }
        when(decoder.decode("token")).thenReturn(token(false, claims -> claims.put("aud", List.of(TaskRecoveryConstant.A2A_AUDIENCE, "extra"))));
        assertDenied();
        when(decoder.decode("token")).thenReturn(token(false, claims -> claims.put("aud", List.of(TaskRecoveryConstant.DRAFT_AUDIENCE))));
        assertDenied();
    }

    @Test
    void forbidsOtherActionsMixedAgentWorkerCapabilitiesMissingAndDuplicateClaims() {
        properties.setTaskRecoveryEnabled(true);
        for (Consumer<Map<String, Object>> mutation : List.<Consumer<Map<String, Object>>>of(
                claims -> claims.put(TaskRecoveryConstant.ACTIONS, List.of(TaskRecoveryConstant.QUERY, "CREATE_NEW_TASK")),
                claims -> claims.put(TaskRecoveryConstant.ACTIONS, List.of(TaskRecoveryConstant.QUERY, TaskRecoveryConstant.QUERY)),
                claims -> claims.remove(TaskRecoveryConstant.ACTIONS),
                claims -> claims.remove(TaskRecoveryConstant.SOURCE_JTI),
                claims -> claims.remove(JwtConstant.CLAIM_EXECUTION_MODE),
                claims -> claims.put(JwtConstant.CLAIM_AGENT_ACTIONS, List.of("WRITE_DRAFT")),
                claims -> claims.put(JwtConstant.CLAIM_WORKER_ACTIONS, List.of("EVALUATE")),
                claims -> claims.put(TaskRecoveryConstant.REMOTE_STATUS, "COMPLETED"))) {
            when(decoder.decode("token")).thenReturn(token(false, mutation));
            assertDenied();
        }
    }

    @Test
    void queryCannotCancelAndCancelCannotBecomeDraftOrGeneralAgentAccess() {
        properties.setTaskRecoveryEnabled(true);
        when(decoder.decode("token")).thenReturn(token(false, claims -> { }));
        assertThatThrownBy(() -> verifier.verify("token", TaskRecoveryConstant.CANCEL, "remote")).isInstanceOf(JwtException.class);
        when(decoder.decode("token")).thenReturn(token(false, claims -> claims.put(TaskRecoveryConstant.ACTIONS,
                List.of(TaskRecoveryConstant.QUERY, TaskRecoveryConstant.CANCEL))));
        verifier.verify("token", TaskRecoveryConstant.CANCEL, "remote");
        assertThatThrownBy(() -> verifier.verifyDraft("token", 1L)).isInstanceOf(JwtException.class);
    }

    @Test
    void initialDocumentVersionZeroIsAValidFrozenBaselineButNegativeVersionIsNot() {
        properties.setTaskRecoveryEnabled(true);
        when(decoder.decode("token")).thenReturn(token(false, claims -> claims.put(JwtConstant.CLAIM_DOCUMENT_VERSION_SNAPSHOT, 0L)));
        verifier.verify("token", TaskRecoveryConstant.QUERY, "remote");
        when(decoder.decode("token")).thenReturn(token(false, claims -> claims.put(JwtConstant.CLAIM_DOCUMENT_VERSION_SNAPSHOT, -1L)));
        assertDenied();
    }

    @Test
    void enforcesStrictExpiryFutureIatAndMaximumTtlIndependentlyOfDecoderClockSkew() {
        properties.setTaskRecoveryEnabled(true);
        for (Consumer<Map<String, Object>> mutation : List.<Consumer<Map<String, Object>>>of(
                claims -> claims.put("exp", Instant.now().minusSeconds(1)),
                claims -> claims.put("iat", Instant.now().plusSeconds(1)),
                claims -> claims.put("nbf", Instant.now().plusSeconds(1)),
                claims -> claims.put("exp", Instant.now().plusSeconds(400)))) {
            when(decoder.decode("token")).thenReturn(token(false, mutation));
            assertDenied();
        }
    }

    @Test
    void draftRequiresLiveAndOneTerminalSpecificActionAndHasSeparateTtl() {
        properties.setTaskRecoveryEnabled(true);
        Jwt draft = token(true, claims -> { });
        when(decoder.decode("token")).thenReturn(draft);
        verifier.verifyDraft("token", 1L);
        assertThatThrownBy(() -> verifier.verify("token", TaskRecoveryConstant.QUERY, "remote")).isInstanceOf(JwtException.class);
        for (Consumer<Map<String, Object>> mutation : List.<Consumer<Map<String, Object>>>of(
                claims -> claims.put(JwtConstant.CLAIM_EXECUTION_MODE, "ISOLATED"),
                claims -> claims.put(TaskRecoveryConstant.REMOTE_STATUS, "WORKING"),
                claims -> claims.put(TaskRecoveryConstant.REMOTE_STATUS, "FAILED"),
                claims -> claims.put(TaskRecoveryConstant.ACTIONS, List.of(TaskRecoveryConstant.FINALIZE, TaskRecoveryConstant.DISCARD)),
                claims -> claims.put("exp", Instant.now().plusSeconds(70)))) {
            when(decoder.decode("token")).thenReturn(token(true, mutation));
            assertThatThrownBy(() -> verifier.verifyDraft("token", 1L)).isInstanceOf(JwtException.class);
        }
        when(decoder.decode("token")).thenReturn(token(true, claims -> {
            claims.put(TaskRecoveryConstant.REMOTE_STATUS, "CANCELED");
            claims.put(TaskRecoveryConstant.ACTIONS, List.of(TaskRecoveryConstant.DISCARD));
        }));
        verifier.verifyDraft("token", 1L);
    }

    private void assertDenied() {
        assertThatThrownBy(() -> verifier.verify("token", TaskRecoveryConstant.QUERY, "remote")).isInstanceOf(JwtException.class);
    }

    private Jwt token(boolean draft, Consumer<Map<String, Object>> mutation) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(TaskRecoveryConstant.SERVICE)
                .issuer(properties.getTaskRecoveryIssuer()).issuedAt(Instant.now().minusSeconds(10))
                .notBefore(Instant.now().minusSeconds(10)).expiresAt(Instant.now().plusSeconds(draft ? 30 : 120))
                .audience(List.of(draft ? TaskRecoveryConstant.DRAFT_AUDIENCE : TaskRecoveryConstant.A2A_AUDIENCE))
                .claims(claims -> {
                    identity.toClaims().forEach((key, value) -> { if (value != null) { claims.put(key, value); } });
                    claims.put(JwtConstant.CLAIM_ACTOR_TYPE, JwtConstant.ACTOR_SERVICE);
                    claims.put(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_SERVICE);
                    claims.put(JwtConstant.CLAIM_SERVICE, TaskRecoveryConstant.SERVICE);
                    claims.put(TaskRecoveryConstant.PURPOSE, draft ? TaskRecoveryConstant.DRAFT_PURPOSE : TaskRecoveryConstant.A2A_PURPOSE);
                    claims.put(TaskRecoveryConstant.ACTIONS, List.of(draft ? TaskRecoveryConstant.FINALIZE : TaskRecoveryConstant.QUERY));
                    claims.put(TaskRecoveryConstant.RECOVERY_ID, UUID.randomUUID().toString());
                    claims.put(TaskRecoveryConstant.A2A_TASK_ID, "remote");
                    claims.put(TaskRecoveryConstant.SOURCE_JTI, "source");
                    claims.put("jti", "new");
                    if (draft) { claims.put(TaskRecoveryConstant.REMOTE_STATUS, "COMPLETED"); }
                    mutation.accept(claims);
                }).build();
    }
}
