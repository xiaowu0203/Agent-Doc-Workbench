package com.agentdoc.auth.service;

import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.auth.config.TaskRecoverySigningProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.TaskRecoveryIdentityDTO;
import com.agentdoc.common.feign.dto.TaskRecoveryIssueDTO;
import com.agentdoc.common.utils.TaskRecoveryJwtUtils;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskRecoveryIssuanceServiceTest {
    private static JwtService jwtService;
    private static NimbusJwtEncoder encoder;
    private static NimbusJwtDecoder normalDecoder;
    private TaskRecoverySigningProperties properties;
    private TaskRecoveryIssuanceService service;
    // 测试身份在运行时生成，仓库不保存密钥。
    private final String machineKey = UUID.randomUUID() + UUID.randomUUID().toString();
    private final TaskRecoveryIdentityDTO identity = new TaskRecoveryIdentityDTO(
            10L, 20L, 30L, 40L, "LIVE", 5L, "a".repeat(64), 1, "b".repeat(64), null);

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        JwtProperties config = new JwtProperties(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()), Duration.ofMinutes(30),
                Duration.ofDays(7), "agent-doc-workbench");
        jwtService = new JwtService(config);
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test-generated").build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        normalDecoder = NimbusJwtDecoder.withPublicKey(jwtService.getPublicKey()).build();
    }

    @BeforeEach
    void setUp() {
        properties = new TaskRecoverySigningProperties();
        properties.setMachineKey(machineKey);
        service = new TaskRecoveryIssuanceService(jwtService, properties);
    }

    @Test
    void acceptsExpiredProofOnlyForNarrowRecoveryAndLeavesNormalExpiryIntact() {
        String proof = proof(builder -> { });
        assertThatThrownBy(() -> normalDecoder.decode(proof)).isInstanceOf(JwtException.class);
        Jwt token = normalDecoder.decode(service.issue(machineKey, request(proof, false, null), false));
        assertThat(token.getAudience()).containsExactly(TaskRecoveryConstant.A2A_AUDIENCE);
        assertThat(token.getClaimAsString(TaskRecoveryConstant.PURPOSE)).isEqualTo(TaskRecoveryConstant.A2A_PURPOSE);
        assertThat(token.getClaimAsStringList(TaskRecoveryConstant.ACTIONS)).containsExactly(TaskRecoveryConstant.QUERY);
        assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt()).getSeconds()).isEqualTo(300);
        assertThat(token.hasClaim(JwtConstant.CLAIM_AGENT_ACTIONS)).isFalse();
        assertThatThrownBy(() -> TaskRecoveryJwtUtils.rejectGeneralAccess(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void cancelRequiresRecordedIntentAndDraftUsesIndependentAudienceAndSingleAction() {
        Jwt query = normalDecoder.decode(service.issue(machineKey, request(proof(b -> { }), true, null), false));
        assertThat(query.getClaimAsStringList(TaskRecoveryConstant.ACTIONS)).containsExactly(
                TaskRecoveryConstant.QUERY, TaskRecoveryConstant.CANCEL);
        for (String state : List.of("COMPLETED", "FAILED", "CANCELED")) {
            Jwt draft = normalDecoder.decode(service.issue(machineKey, request(proof(b -> { }), false, state), true));
            assertThat(draft.getAudience()).containsExactly(TaskRecoveryConstant.DRAFT_AUDIENCE);
            assertThat(draft.getClaimAsStringList(TaskRecoveryConstant.ACTIONS)).containsExactly(
                    state.equals("COMPLETED") ? TaskRecoveryConstant.FINALIZE : TaskRecoveryConstant.DISCARD);
            assertThat(Duration.between(draft.getIssuedAt(), draft.getExpiresAt()).getSeconds()).isEqualTo(60);
        }
    }

    @Test
    void rejectsWrongMissingAndUnconfiguredMachineIdentity() {
        TaskRecoveryIssueDTO request = request(proof(b -> { }), false, null);
        assertThatThrownBy(() -> service.issue(null, request, false)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.issue(UUID.randomUUID().toString(), request, false)).isInstanceOf(BusinessException.class);
        properties.setMachineKey("");
        assertThatThrownBy(() -> service.issue(machineKey, request, false)).isInstanceOf(BusinessException.class);
    }

    @Test
    void previousMachineKeyExpiresAfterFiveMinutesAndCanBeRevokedImmediately() {
        properties.setPreviousMachineKey(machineKey);
        properties.setMachineKey(UUID.randomUUID() + UUID.randomUUID().toString());
        properties.setKeyRotatedAt(Instant.now().minusSeconds(30));
        service.issue(machineKey, request(proof(b -> { }), false, null), false);
        properties.setKeyRotatedAt(Instant.now().minusSeconds(301));
        assertThatThrownBy(() -> service.issue(machineKey, request(proof(b -> { }), false, null), false))
                .isInstanceOf(BusinessException.class);
        properties.setKeyRotatedAt(Instant.now());
        properties.setPreviousMachineKey("");
        assertThatThrownBy(() -> service.issue(machineKey, request(proof(b -> { }), false, null), false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsInvalidIssuerAudienceActorScopeTimeAndEveryFrozenFieldMismatch() {
        List<Consumer<JwtClaimsSet.Builder>> invalid = List.of(
                b -> b.issuer("another-issuer"), b -> b.audience(List.of("another-audience")),
                b -> b.claim(JwtConstant.CLAIM_ACTOR_TYPE, "SERVICE"),
                b -> b.claim(JwtConstant.CLAIM_SCOPE, "user"),
                b -> b.issuedAt(Instant.now().plusSeconds(60)).expiresAt(Instant.now().plusSeconds(120)),
                b -> b.notBefore(Instant.now().plusSeconds(60)),
                b -> b.expiresAt(Instant.now().plusSeconds(60)), b -> b.subject("11"));
        invalid.forEach(change -> assertThatThrownBy(() -> service.issue(machineKey,
                request(proof(change), false, null), false)).isInstanceOf(BusinessException.class));
        identity.toClaims().forEach((field, value) -> assertThatThrownBy(() -> service.issue(machineKey,
                request(proof(b -> b.claim(field, "mismatch")), false, null), false)).isInstanceOf(BusinessException.class));
        String proof = proof(b -> { });
        String[] parts = proof.split("\\.");
        String signature = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertThatThrownBy(() -> service.issue(machineKey, request(parts[0] + "." + parts[1] + "." + signature,
                false, null), false)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsDraftWithoutOriginalWritePermissionUnknownTerminalAndIsolatedMode() {
        assertThatThrownBy(() -> service.issue(machineKey, request(proof(b -> b.claim(
                JwtConstant.CLAIM_AGENT_ACTIONS, List.of("READ_FRAGMENT"))), false, "COMPLETED"), true))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.issue(machineKey, request(proof(b -> { }), false, "RUNNING"), true))
                .isInstanceOf(BusinessException.class);
        TaskRecoveryIdentityDTO isolated = new TaskRecoveryIdentityDTO(10L, 20L, 30L, 40L, "ISOLATED",
                5L, "a".repeat(64), 1, "b".repeat(64), null);
        TaskRecoveryIssueDTO request = new TaskRecoveryIssueDTO(proof(b -> b.claim(
                JwtConstant.CLAIM_EXECUTION_MODE, "ISOLATED")), isolated, "existing-a2a", UUID.randomUUID().toString(),
                false, "COMPLETED");
        assertThatThrownBy(() -> service.issue(machineKey, request, true)).isInstanceOf(BusinessException.class);
    }

    @Test
    void userAndWorkerTokensCannotBeHistoricalProofAndRequestsHideCredentials() {
        String worker = jwtService.createEvaluationWorkerCapabilityToken(10L, 30L, "c".repeat(64), 300, List.of());
        assertThatThrownBy(() -> service.issue(machineKey, request(worker, false, null), false))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.issue(machineKey, request(proof(b -> b.claim(
                JwtConstant.CLAIM_SCOPE, "user")), false, null), false)).isInstanceOf(BusinessException.class);
        assertThat(request(proof(b -> { }), false, null).toString()).doesNotContain("eyJ");
        assertThat(properties.toString()).doesNotContain(machineKey);
    }

    private TaskRecoveryIssueDTO request(String proof, boolean cancel, String state) {
        return new TaskRecoveryIssueDTO(proof, identity, "existing-a2a", UUID.randomUUID().toString(), cancel, state);
    }

    private String proof(Consumer<JwtClaimsSet.Builder> change) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder().issuer("agent-doc-workbench")
                .issuedAt(now.minusSeconds(21_600)).expiresAt(now.minusSeconds(120)).subject("10")
                .id(UUID.randomUUID().toString()).audience(List.of(JwtConstant.TASK_CAPABILITY_AUDIENCE))
                .claim(JwtConstant.CLAIM_ACTOR_TYPE, JwtConstant.ACTOR_AGENT)
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_AGENT)
                .claim(JwtConstant.CLAIM_AGENT_ACTIONS, List.of("READ_FRAGMENT", "WRITE_DRAFT"));
        identity.toClaims().forEach((name, value) -> { if (value != null) { builder.claim(name, value); } });
        change.accept(builder);
        return encoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
    }
}
