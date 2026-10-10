package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.feign.*;
import com.agentdoc.common.feign.dto.OnlineCapabilityIssueDTO;
import com.agentdoc.common.feign.vo.OnlineTaskFactVO;
import com.agentdoc.common.feign.vo.OnlineExecutionFactVO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.config.OnlineReconciliationProperties;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OnlineReconciliationServiceTest {
    private final OnlineExperimentMapper experiments = mock(OnlineExperimentMapper.class);
    private final OnlineAssignmentMapper assignments = mock(OnlineAssignmentMapper.class);
    private final OnlineExecutionAuthority authority = mock(OnlineExecutionAuthority.class);
    private final OnlineControlAccessService control = mock(OnlineControlAccessService.class);
    private final OnlineAuthFeign auth = mock(OnlineAuthFeign.class);
    private final OnlineAgentFeign agent = mock(OnlineAgentFeign.class);
    private final OnlineTaskFeign task = mock(OnlineTaskFeign.class);
    private final OnlineDocumentFeign document = mock(OnlineDocumentFeign.class);
    private final OnlineCapabilityVerifier verifier = mock(OnlineCapabilityVerifier.class);
    private final OnlineExperimentEntity experiment = new OnlineExperimentEntity();
    private final OnlineTaskFactVO execution = new OnlineTaskFactVO("10", "a".repeat(64), null, "71", "COMPLETED", null, false, false, LocalDateTime.now().toString());
    private OnlineReconciliationService service;
    @BeforeEach void setup() {
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("verifier", verifier);
        service = new OnlineReconciliationService(new OnlineReconciliationProperties(), experiments, assignments, authority, control,
                mock(WorkerCapabilityCryptoService.class), auth, agent, task, document, beans.getBeanProvider(OnlineCapabilityVerifier.class));
        experiment.setId(11L); experiment.setStatus("ACTIVE"); experiment.setStateVersion(0L); experiment.setAssignmentDeadline(LocalDateTime.now().plusHours(1));
        when(experiments.selectById(11L)).thenReturn(experiment); when(control.require(11L)).thenReturn("control");
        when(verifier.verify(anyString(), any(), anyString(), anyList())).thenReturn(Jwt.withTokenValue("control").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(299)).build());
        when(authority.manifest(experiment)).thenReturn(OnlineProtocolUtils.object("{\"dependencyHash\":\"current\"}"));
        when(agent.dependency("11", "control")).thenReturn(Result.ok("current")); when(document.requireResources("11", "control")).thenReturn(Result.ok());
        when(task.repair("11", "control", List.of("10"))).thenReturn(Result.ok());
        var row = new OnlineAssignmentEntity(); row.setId(61L); row.setTaskId(10L); when(assignments.selectList(any())).thenReturn(List.of(row));
        when(auth.issue(eq("control"), any())).thenAnswer(call -> Result.ok(call.<OnlineCapabilityIssueDTO>getArgument(1).purpose().name()));
        when(task.facts("11", "ONLINE_OBSERVE", List.of("10"))).thenReturn(Result.ok(List.of(new OnlineTaskFactVO("10", "a".repeat(64), "COMPLETED", "71", null, "7", false, false, null))));
        when(agent.facts("11", "ONLINE_OBSERVE", List.of("10"))).thenReturn(Result.ok(List.of(new OnlineExecutionFactVO(execution, "remote", "context", null))));
    }
    @Test void oneSourceOutageStillPassesIndependentAgentTerminalButNeverInventsTaskLedger() {
        when(task.facts(anyString(), anyString(), anyList())).thenThrow(new IllegalStateException("offline")); service.reconcile(11L);
        verify(authority).reconcile(11L, 10L, null, execution); verifyNoMoreInteractionsExceptProtection();
    }
    private void verifyNoMoreInteractionsExceptProtection() { verify(auth, never()).issue(eq("control"), argThat(request -> request.purpose() == OnlineCapabilityPurpose.ONLINE_CANCEL)); }
    @Test void emergencyCancellationUsesAcceptedBatchAndDoesNotPreventIndependentObservationAfterOneCancelFails() {
        experiment.setEmergencyStopRequestedAt(LocalDateTime.now()); when(task.cancel(anyString(), anyString(), anyList())).thenReturn(Result.fail(503, "offline"));
        when(agent.cancel(anyString(), anyString(), anyList())).thenReturn(Result.ok()); service.reconcile(11L);
        verify(agent).cancel("11", "ONLINE_CANCEL", List.of("10")); verify(task).cancel("11", "ONLINE_CANCEL", List.of("10"));
        verify(authority).reconcile(eq(11L), eq(10L), any(OnlineTaskFactVO.class), eq(execution));
    }
    @Test void dependencyDriftPausesWithoutDiscardingFactsAndDefaultDeploymentFlagDoesNotScan() {
        service.scan(); verify(experiments, never()).selectList(any());
        when(agent.dependency("11", "control")).thenReturn(Result.ok("changed")); service.reconcile(11L);
        verify(authority).safetyPause(11L, "DEPENDENCY_DRIFT"); verify(authority).reconcile(eq(11L), eq(10L), any(), eq(execution));
    }
}
