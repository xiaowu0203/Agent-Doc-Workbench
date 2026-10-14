package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineAuthFeign;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentStartDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentStateDTO;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentPreflightVO;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentActionVO;
import com.agentdoc.evaluation.enums.OnlineExperimentAction;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OnlineExperimentLifecycleServiceTest {
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final OnlineExperimentService queries = mock(OnlineExperimentService.class);
    private final OnlineLifecyclePersistenceService persistence = mock(OnlineLifecyclePersistenceService.class);
    private final OnlineAuthFeign auth = mock(OnlineAuthFeign.class);
    private final SpaceAccessService access = mock(SpaceAccessService.class);
    private OnlineExperimentLifecycleService service;
    @BeforeEach void setup() {
        var beans = new DefaultListableBeanFactory();
        service = new OnlineExperimentLifecycleService(experiments, queries, mock(OnlineExecutionAuthority.class), persistence, access, auth,
                mock(WorkerCapabilityCryptoService.class), beans.getBeanProvider(OnlineCapabilityVerifier.class));
        var experiment = new OnlineExperimentEntity(); experiment.setId(11L); experiment.setSpaceId(20L); experiment.setManifestHash("a".repeat(64));
        when(experiments.selectById(11L)).thenReturn(experiment);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("user").header("alg", "RS256")
                .subject("50").claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER).build()));
        when(queries.preflight("11")).thenReturn(new OnlineExperimentPreflightVO("11", "a".repeat(64), null, "b".repeat(64), null, true, false, 1, 1, null, "100", null, List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void completeReadinessIsRequiredDespiteAllHumanAcknowledgementsAndBothVariants() {
        var request = new OnlineExperimentStartDTO("start", "a".repeat(64), "0", "b".repeat(64), true, true, true, true);
        assertThatThrownBy(() -> service.start("11", request)).isInstanceOf(BusinessException.class).hasMessageContaining("ONLINE_EXECUTION_NOT_READY");
        verifyNoInteractions(auth); verify(persistence, never()).apply(any(), any(), any(), anyString(), anyString(), anyString(), any(), anyString(), any(), any(), any());
        verify(access).requireOwner(20L);
    }
    @Test void identicalActionRetryReturnsStoredSnapshotBeforeNewPreflightOrAuthorization() {
        var previous = new OnlineExperimentActionVO("11", "PAUSED", "1", "a".repeat(64), "PAUSE", 1, null, null, null, null);
        when(persistence.prior(eq(11L), eq(50L), eq(OnlineExperimentAction.PAUSE), eq("pause"), anyString())).thenReturn(previous);
        assertThat(service.state("11", OnlineExperimentAction.PAUSE, new OnlineExperimentStateDTO("pause", "a".repeat(64), "0", "复查"))).isSameAs(previous);
        verifyNoInteractions(auth, queries); verify(access).requireOwner(20L);
    }

    @Test void startUsesVerifiedProtectionGrantBeforePersistingState() {
        allowStartWithControl("50");
        var expected = new OnlineExperimentActionVO("11", "ACTIVE", "1", "a".repeat(64), "START", 1, null, null, null, null);
        when(persistence.apply(any(), any(), any(), anyString(), anyString(), anyString(), any(), anyString(), any(), any(), any())).thenReturn(expected);
        assertThat(service.start("11", startRequest())).isSameAs(expected);
        var order = inOrder(auth, persistence);
        order.verify(auth).authorize(any());
        order.verify(persistence).apply(eq(11L), eq(50L), eq(OnlineExperimentAction.START), eq("start"), anyString(),
                eq("a".repeat(64)), eq(0L), anyString(), eq("b".repeat(64)), eq("d".repeat(64)),
                argThat(grant -> "test-v1".equals(grant.keyVersion()) && "synthetic-ciphertext".equals(grant.ciphertext())));
    }

    @Test void failedIssuerOrWrongAuthorizedActorCannotPersistStartedState() {
        allowStartWithControl("51");
        assertThatThrownBy(() -> service.start("11", startRequest())).isInstanceOf(BusinessException.class);
        verify(persistence, never()).apply(any(), any(), any(), anyString(), anyString(), anyString(), any(), anyString(), any(), any(), any());
        when(auth.authorize(any())).thenReturn(Result.fail(ErrorCode.FORBIDDEN, "online issuer unavailable"));
        assertThatThrownBy(() -> service.start("11", startRequest())).isInstanceOf(BusinessException.class);
        verify(persistence, never()).apply(any(), any(), any(), anyString(), anyString(), anyString(), any(), anyString(), any(), any(), any());
    }

    private OnlineExperimentStartDTO startRequest() {
        return new OnlineExperimentStartDTO("start", "a".repeat(64), "0", "b".repeat(64), true, true, true, true);
    }

    private void allowStartWithControl(String actor) {
        var authority = mock(OnlineExecutionAuthority.class);
        when(authority.manifest(any())).thenReturn(JsonUtils.parseStrict("{\"dependencyHash\":\"" + "d".repeat(64) + "\"}", JsonNode.class));
        var crypto = mock(WorkerCapabilityCryptoService.class);
        when(crypto.encrypt("synthetic-control")).thenReturn(new WorkerCapabilityCryptoService.EncryptedCapability("test-v1", "synthetic-ciphertext"));
        var verifier = mock(OnlineCapabilityVerifier.class);
        var control = Jwt.withTokenValue("synthetic-control").header("alg", "RS256").subject("service").expiresAt(Instant.now().plusSeconds(300))
                .claim(OnlineCapabilityConstant.MANIFEST_HASH, "a".repeat(64)).claim(OnlineCapabilityConstant.AUTHORIZED_BY, actor)
                .claim(JwtConstant.CLAIM_SPACE_ID, "20").build();
        when(verifier.verify("synthetic-control", OnlineCapabilityPurpose.ONLINE_CONTROL, "11", List.of())).thenReturn(control);
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("onlineVerifier", verifier);
        service = new OnlineExperimentLifecycleService(experiments, queries, authority, persistence, access, auth, crypto,
                beans.getBeanProvider(OnlineCapabilityVerifier.class));
        when(auth.authorize(any())).thenReturn(Result.ok("synthetic-control"));
        when(queries.preflight("11")).thenReturn(new OnlineExperimentPreflightVO("11", "a".repeat(64), null, "b".repeat(64), null,
                true, true, 1, 1, null, "100", null, List.of()));
    }
}
