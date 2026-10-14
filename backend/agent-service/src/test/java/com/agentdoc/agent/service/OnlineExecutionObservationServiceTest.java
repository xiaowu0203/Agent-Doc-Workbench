package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OnlineExecutionObservationServiceTest {
    private final AgentExecutionMapper executions = mock(AgentExecutionMapper.class);
    private final AgentExecutionQueryService query = mock(AgentExecutionQueryService.class);
    private final OnlineEvaluationFeign evaluation = mock(OnlineEvaluationFeign.class);
    private final OnlineDocumentFeign document = mock(OnlineDocumentFeign.class);
    private final OnlineTaskBindingDTO binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000,
            "50", "10", "20", "30", "40", "1", "b".repeat(64), 1, "c".repeat(64), "71", 2, "d".repeat(64),
            "e".repeat(64), "f".repeat(64), "100", 600, "LIVE", "ORIGINAL", 2);
    private final AgentExecutionEntity row = new AgentExecutionEntity();
    private OnlineExecutionObservationService service;
    @BeforeEach void setup() {
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("verifier", mock(OnlineCapabilityVerifier.class));
        service = new OnlineExecutionObservationService(executions, query, evaluation, document, beans.getBeanProvider(OnlineCapabilityVerifier.class));
        row.setId(81L); row.setWorkbenchTaskId(10L); row.setSpaceId(20L); row.setAgentId(30L); row.setOnlineExperimentId(11L);
        row.setOnlineAssignmentId(61L); row.setOnlineBindingSchemaVersion(2); row.setOnlineBindingHash(OnlineProtocolUtils.hash("online.binding", binding));
        row.setStatus("COMPLETED"); row.setFinishedAt(LocalDateTime.now());
        when(evaluation.scopedBindings(eq("11"), any(), eq("proof"), eq(List.of("10")))).thenReturn(Result.ok(List.of(binding)));
        when(document.scopedProtectionPermission(eq("11"), any(), eq("proof"), eq(List.of("10")))).thenReturn(Result.ok());
        when(executions.selectList(any())).thenReturn(List.of(row));
    }
    @Test void pricingFailureKeepsIndependentActualTerminalAndDoesNotFabricateUsage() {
        when(query.tokenUsageOf(row)).thenThrow(new IllegalStateException("unavailable"));
        var result = service.facts("11", "proof", List.of("10")).getFirst();
        assertThat(result.fact().executionStatus()).isEqualTo("COMPLETED"); assertThat(result.fact().executionFinishedAt()).isEqualTo(row.getFinishedAt().toString());
        assertThat(result.tokenUsage()).isNull(); verify(executions, never()).update(any(), any());
    }
    @Test void expiredNonterminalBecomesUnknownAndRevokedOwnerCannotCancel() {
        row.setStatus("WORKING"); row.setFinishedAt(null); row.setStartedAt(LocalDateTime.now().minusSeconds(601));
        assertThat(service.facts("11", "proof", List.of("10")).getFirst().fact().executionStatus()).isEqualTo("UNKNOWN");
        when(document.scopedProtectionPermission(anyString(), any(), anyString(), anyList())).thenReturn(Result.fail(403, "revoked"));
        assertThatThrownBy(() -> service.cancel("11", "proof", List.of("10"))).isInstanceOf(BusinessException.class);
        verify(executions, never()).update(any(), any()); assertThat(row.getStatus()).isEqualTo("WORKING");
    }
}
