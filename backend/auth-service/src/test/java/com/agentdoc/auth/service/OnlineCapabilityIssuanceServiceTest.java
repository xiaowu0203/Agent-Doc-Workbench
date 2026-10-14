package com.agentdoc.auth.service;

import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import static com.agentdoc.common.enums.OnlineCapabilityPurpose.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.auth.config.SecurityConfig;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.dto.OnlineCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.OnlineControlAuthorizeDTO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineAuthorizationProofVO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

class OnlineCapabilityIssuanceServiceTest {
    private static final JwtService signer = new JwtService(new JwtProperties("", "", Duration.ofMinutes(30),
            Duration.ofDays(7), "agent-doc-workbench"));
    private final OnlineDocumentFeign onlineDocument = mock(OnlineDocumentFeign.class);
    private final OnlineEvaluationFeign evaluation = mock(OnlineEvaluationFeign.class);
    private final SecurityVerifyProperties properties = new SecurityVerifyProperties();
    private final JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signer.getPublicKey()).build();
    private final OnlineCapabilityVerifier verifier = new OnlineCapabilityVerifier(decoder, properties);
    private final OnlineCapabilityIssuanceService service = new OnlineCapabilityIssuanceService(signer, properties,
            verifier, onlineDocument, evaluation);
    private final OnlineAuthorizationProofVO proof = new OnlineAuthorizationProofVO("11", "22", "a".repeat(64), 2, "CREATED", null);

    @BeforeEach
    void setup() {
        properties.setOnlineCapabilityEnabled(true);
        login("501");
        when(evaluation.humanProof("11")).thenReturn(Result.ok(proof));
        when(evaluation.controlProof(eq("11"), anyString())).thenReturn(Result.ok(new OnlineAuthorizationProofVO("11", "22", proof.manifestHash(), 2, "ACTIVE", "501")));
        when(onlineDocument.requireHumanProtectionPermission("22")).thenReturn(Result.ok());
        when(onlineDocument.requireProtectionPermission(eq("11"), anyString())).thenReturn(Result.ok());
    }
    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void humanMustExplicitlyAcknowledgeAndCurrentOwnerHasBothPermissions() {
        assertThatThrownBy(() -> service.authorize(new OnlineControlAuthorizeDTO("11", proof.manifestHash(), false)))
                .isInstanceOf(BusinessException.class);
        when(onlineDocument.requireHumanProtectionPermission("22")).thenReturn(Result.fail(ErrorCode.FORBIDDEN));
        assertThatThrownBy(this::control).isInstanceOf(BusinessException.class);
        when(onlineDocument.requireHumanProtectionPermission("22")).thenReturn(Result.ok());
        var jwt = verifier.verify(control(), ONLINE_CONTROL, "11", List.of());
        assertThat(jwt.getClaimAsString(AUTHORIZED_BY)).isEqualTo("501");
        assertThat(jwt.getSubject()).isEqualTo("evaluation-service");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("service");
        assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(300);
        assertThat(jwt.getClaims()).doesNotContainKeys("agentActions", "workerActions", "platformRoles", TASK_SET_HASH);
        assertThatThrownBy(() -> new SecurityConfig(signer).jwtDecoder().decode(jwt.getTokenValue())).isInstanceOf(JwtException.class);
    }

    @Test
    void renewalRetainsOriginalActorEvenWithAnotherAmbientUserAndRechecksRevocation() {
        String source = control(); login("999");
        String renewed = service.issue(source, new OnlineCapabilityIssueDTO(ONLINE_CONTROL, List.of()));
        var jwt = verifier.verify(renewed, ONLINE_CONTROL, "11", List.of());
        assertThat(jwt.getClaimAsString(AUTHORIZED_BY)).isEqualTo("501");
        assertThat(jwt.getClaimAsString(MANIFEST_HASH)).isEqualTo(proof.manifestHash());
        assertThat(jwt.getId()).isNotEqualTo(decoder.decode(source).getId());
        when(onlineDocument.requireProtectionPermission("11", source)).thenReturn(Result.fail(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.issue(source, new OnlineCapabilityIssueDTO(ONLINE_CONTROL, List.of())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void derivesOnlyConfirmedSetAndDoesNotOutliveSourceOrBecomeControl() {
        String source = control();
        when(evaluation.bindings("11", source, List.of("2", "10")))
                .thenReturn(Result.ok(List.of(binding("2", "22"), binding("10", "22"))));
        for (var purpose : List.of(ONLINE_OBSERVE, ONLINE_CANCEL)) {
            String derived = service.issue(source, new OnlineCapabilityIssueDTO(purpose, List.of("10", "2")));
            var jwt = verifier.verify(derived, purpose, "11", List.of("2", "10"));
            assertThat(jwt.getClaimAsString(TASK_SET_HASH)).isEqualTo(OnlineCapabilityUtils.taskSetHash(List.of("2", "10")));
            assertThat(jwt.getExpiresAt()).isBeforeOrEqualTo(decoder.decode(source).getExpiresAt());
            assertThatThrownBy(() -> service.issue(derived, new OnlineCapabilityIssueDTO(ONLINE_CONTROL, List.of())))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> new SecurityConfig(signer).jwtDecoder().decode(derived)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void missingDuplicatedOrForeignBindingsAndManifestDriftCannotMint() {
        String source = control();
        for (List<OnlineTaskBindingDTO> values : List.of(List.<OnlineTaskBindingDTO>of(),
                List.of(binding("2", "33")), List.of(binding("3", "22")))) {
            when(evaluation.bindings("11", source, List.of("2"))).thenReturn(Result.ok(values));
            assertThatThrownBy(() -> service.issue(source, new OnlineCapabilityIssueDTO(ONLINE_CANCEL, List.of("2"))))
                    .isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> service.issue(source, new OnlineCapabilityIssueDTO(ONLINE_OBSERVE, List.of("2", "2"))))
                .isInstanceOf(BusinessException.class);
        when(evaluation.controlProof("11", source)).thenReturn(Result.ok(new OnlineAuthorizationProofVO("11", "22",
                "b".repeat(64), 2, "ACTIVE", "501")));
        assertThatThrownBy(() -> service.issue(source, new OnlineCapabilityIssueDTO(ONLINE_CONTROL, List.of())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void expiredControlWrongSignatureAndFeatureOffCannotRenew() {
        Jwt original = decoder.decode(control());
        JwtEncoder encoder = (JwtEncoder) ReflectionTestUtils.getField(signer, "encoder");
        var claims = JwtClaimsSet.builder().claims(values -> values.putAll(original.getClaims()))
                .issuedAt(Instant.now().minusSeconds(301)).notBefore(Instant.now().minusSeconds(301))
                .expiresAt(Instant.now().minusSeconds(1)).build();
        String expired = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        assertThatThrownBy(() -> service.issue(expired, new OnlineCapabilityIssueDTO(ONLINE_CONTROL, List.of())))
                .isInstanceOf(BusinessException.class);
        JwtService otherSigner = new JwtService(signer.props());
        String forged = otherSigner.createOnlineCapability(proof, "501", ONLINE_CONTROL, null, null);
        assertThatThrownBy(() -> service.issue(forged, new OnlineCapabilityIssueDTO(ONLINE_CONTROL, List.of())))
                .isInstanceOf(BusinessException.class);
        properties.setOnlineCapabilityEnabled(false);
        assertThatThrownBy(this::control).isInstanceOf(BusinessException.class);
        verify(evaluation, never()).controlProof("11", expired);
        verify(evaluation, never()).controlProof("11", forged);
    }

    private String control() { return service.authorize(new OnlineControlAuthorizeDTO("11", proof.manifestHash(), true)); }

    @Test
    void newCurrentOwnerCanExplicitlyReauthorizeButCannotUseItBeforeAuthorityConfirmsTheChange() {
        var prior = new OnlineAuthorizationProofVO("11", "22", proof.manifestHash(), 2, "PAUSED", "502");
        when(evaluation.humanProof("11")).thenReturn(Result.ok(prior));
        String pending = control();
        assertThat(verifier.verify(pending, ONLINE_CONTROL, "11", List.of()).getClaimAsString(AUTHORIZED_BY)).isEqualTo("501");
        when(evaluation.controlProof("11", pending)).thenReturn(Result.ok(prior));
        assertThatThrownBy(() -> service.issue(pending, new OnlineCapabilityIssueDTO(ONLINE_CONTROL, List.of())))
                .isInstanceOf(BusinessException.class);
    }
    private void login(String actor) {
        var jwt = Jwt.withTokenValue("user").header("alg", "RS256").subject(actor).claim("scope", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
    private OnlineTaskBindingDTO binding(String id, String space) {
        return new OnlineTaskBindingDTO("7" + id, "11", proof.manifestHash(), "1", "BASELINE", 9999,
                "601", id, space, "201", "301", "1", "b".repeat(64), 1, "c".repeat(64), "401", 2,
                "d".repeat(64), "e".repeat(64), "f".repeat(64), "500", 60, "LIVE", "ORIGINAL", 2);
    }
}
