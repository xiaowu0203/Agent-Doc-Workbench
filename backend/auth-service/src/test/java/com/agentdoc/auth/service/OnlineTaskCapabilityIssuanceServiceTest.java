package com.agentdoc.auth.service;

import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.feign.vo.OnlineTaskDispatchProofVO;
import com.agentdoc.common.feign.vo.OnlineSlotPermitVO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;

class OnlineTaskCapabilityIssuanceServiceTest {
    private static final JwtService signer = new JwtService(new JwtProperties("", "", Duration.ofMinutes(30), Duration.ofDays(7), "agent-doc-workbench"));
    private final OnlineTaskFeign tasks = mock(OnlineTaskFeign.class);
    private final OnlineEvaluationFeign evaluation = mock(OnlineEvaluationFeign.class);
    private final SecurityVerifyProperties properties = new SecurityVerifyProperties();
    private final NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signer.getPublicKey()).build();
    private final OnlineCapabilityVerifier verifier = new OnlineCapabilityVerifier(decoder, properties);
    private final OnlineTaskCapabilityIssuanceService service = new OnlineTaskCapabilityIssuanceService(signer, properties, verifier, tasks, evaluation);
    private final OnlineTaskBindingDTO binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000,
            "50", "10", "20", "30", "40", "1", "b".repeat(64), 1, "c".repeat(64), "71", 2, "d".repeat(64),
            "e".repeat(64), "f".repeat(64), "100", 600, "LIVE", "ORIGINAL", 2);
    private final OnlineAssignmentRequestDTO request = new OnlineAssignmentRequestDTO("10", "20", "30", "40", "50", "original-key",
            "a".repeat(64), "c".repeat(64), "1", "b".repeat(64), "100", 1);
    private final OnlineTaskDispatchProofVO proof = new OnlineTaskDispatchProofVO(request, binding, "PENDING", List.of(JwtConstant.ACTION_READ_FRAGMENT), "f".repeat(64));
    @BeforeEach void setup() {
        properties.setOnlineCapabilityEnabled(true);
        var user = Jwt.withTokenValue("user").header("alg", "RS256").subject("50").claim("scope", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(user));
        when(tasks.humanDispatchProof("10")).thenReturn(Result.ok(proof));
        when(tasks.waitDispatchProof(eq("10"), anyString())).thenReturn(Result.ok(proof));
        when(evaluation.waiting(eq("10"), anyString())).thenReturn(Result.ok(binding));
        when(evaluation.permit(eq("10"), anyString())).thenReturn(Result.ok(new OnlineSlotPermitVO(binding,
                OnlineProtocolUtils.hash("online.binding", binding), 3, "9".repeat(64), false)));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void waitHasNoModelPrivilegeAndExchangeIncludesFullFrozenIdentityAndSlotGeneration() {
        String wait = service.initial("10"); var source = verifier.verifyWait(wait, "10");
        assertThat(source.getClaims()).doesNotContainKeys("agentActions", "workerActions", SLOT_GENERATION, SLOT_PERMIT_HASH);
        var capability = decoder.decode(service.exchange("10", wait));
        assertThat(capability.getAudience()).containsExactly(JwtConstant.TASK_CAPABILITY_AUDIENCE);
        assertThat(capability.getClaimAsString(EXPERIMENT_ID)).isEqualTo("11"); assertThat(capability.getClaimAsString(ASSIGNMENT_ID)).isEqualTo("61");
        assertThat(capability.getClaimAsString(BINDING_HASH)).isEqualTo(OnlineProtocolUtils.hash("online.binding", binding));
        assertThat(capability.getClaimAsString(SLOT_PERMIT_HASH)).isEqualTo("9".repeat(64));
        assertThat(String.valueOf((Object) capability.getClaim(SLOT_GENERATION))).isEqualTo("3");
        assertThatThrownBy(() -> verifier.verifyWait(capability.getTokenValue(), "10")).isInstanceOf(JwtException.class);
    }
    @Test void signedWaitCanOnlyRenewItsOriginalPendingScopeAndCannotExchangeAfterLocalCancellation() {
        String wait = service.initial("10"); String renewed = service.renew("10", wait);
        assertThat(verifier.verifyWait(renewed, "10").getClaimAsString(BINDING_HASH)).isEqualTo(OnlineProtocolUtils.hash("online.binding", binding));
        when(tasks.waitDispatchProof(eq("10"), anyString())).thenReturn(Result.ok(new OnlineTaskDispatchProofVO(request, binding, "TERMINATED", proof.actions(), proof.releaseHash())));
        assertThatThrownBy(() -> service.renew("10", wait)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.exchange("10", wait)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.exchange("11", wait)).isInstanceOf(JwtException.class);
    }
    @Test void anotherHumanAndUnavailableCurrentProtectionNeverMintWaitOrExecution() {
        var user = Jwt.withTokenValue("other").header("alg", "RS256").subject("51").claim("scope", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(user));
        assertThatThrownBy(() -> service.initial("10")).isInstanceOf(BusinessException.class);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("user").header("alg", "RS256").subject("50").claim("scope", "user").build()));
        String wait = service.initial("10"); when(evaluation.waiting(eq("10"), anyString())).thenReturn(Result.fail(403, "revoked"));
        assertThatThrownBy(() -> service.renew("10", wait)).isInstanceOf(BusinessException.class);
    }
}
