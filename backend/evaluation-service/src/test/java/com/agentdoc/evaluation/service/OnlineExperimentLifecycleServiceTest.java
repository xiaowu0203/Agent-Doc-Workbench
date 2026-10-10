package com.agentdoc.evaluation.service;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineAuthFeign;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
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
}
