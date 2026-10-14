package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.OnlineAgentFeign;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.vo.AgentEvaluationEvidenceVO;
import com.agentdoc.common.feign.vo.OnlineReportExecutionFactVO;
import com.agentdoc.common.feign.vo.OnlineReportTaskFactVO;
import com.agentdoc.common.feign.vo.OnlineReportUsageVO;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluatorEngine;
import com.agentdoc.evaluation.evaluator.OnlineRuleContractValidator;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnlineFactEvaluationServiceTest {
    private final OnlineExecutionAuthority authority = mock(OnlineExecutionAuthority.class);
    private final OnlineTaskFeign tasks = mock(OnlineTaskFeign.class);
    private final AgentFeign agents = mock(AgentFeign.class);
    private final OnlineAgentFeign onlineAgents = mock(OnlineAgentFeign.class);
    private final OnlineFactEvaluationService service = new OnlineFactEvaluationService(authority,tasks,agents,onlineAgents,new DeterministicEvaluatorEngine());
    private final OnlineAssignmentEntity assignment = new OnlineAssignmentEntity();
    private final OnlineReportUsageVO usage = new OnlineReportUsageVO(71L,1L,1L,"1","1","CNY",1,"2026-10-13T12:00:00",7L,false,null,false,3L,false);
    private OnlineReportTaskFactVO task(String status, OnlineReportUsageVO tokens) {
        return new OnlineReportTaskFactVO("61","51","a".repeat(64),"AVAILABLE",status,"71",null,null,null,"91","b".repeat(64),null,tokens);
    }
    private OnlineReportExecutionFactVO execution(String identity, String status) {
        return new OnlineReportExecutionFactVO("61","51","a".repeat(64),"AVAILABLE",identity,status,null,null,null,usage,null,null);
    }
    private AgentEvaluationEvidenceVO audit(String status) {
        return new AgentEvaluationEvidenceVO(61L,71L,status,"trace","span",LocalDateTime.of(2026,10,13,12,0),LocalDateTime.of(2026,10,13,12,1),3,0,0);
    }
    @BeforeEach void setup() {
        assignment.setTaskId(61L); assignment.setExecutionId(71L); assignment.setBindingHash("a".repeat(64));
        var binding = new OnlineTaskBindingDTO("51","11","b".repeat(64),"1","BASELINE",9000,"501","61","10","20","41","0","c".repeat(64),1,"d".repeat(64),"81",2,"e".repeat(64),"f".repeat(64),"9".repeat(64),"100",600,"LIVE","ORIGINAL",2);
        when(authority.binding(assignment)).thenReturn(binding);
        when(tasks.reportFacts(any())).thenReturn(Result.ok(List.of(task("COMPLETED",usage))));
        when(onlineAgents.reportFacts(any())).thenReturn(Result.ok(List.of(execution("71","COMPLETED"))));
        when(agents.queryEvaluationEvidence(any())).thenReturn(Result.ok(List.of(audit("COMPLETED"))));
    }
    @Test void bothRulesUseIndependentFrozenExecutionAndLedgerFacts() {
        for (String key : List.of(OnlineRuleContractValidator.TASK,OnlineRuleContractValidator.AUDIT)) {
            var result = service.evaluate(key,"{}","{}",assignment);
            assertThat(result.status().name()).isEqualTo("PASSED");
            assertThat(result.metrics().getFirst().value().booleanValue()).isTrue();
            assertThat(result.evidence()).allSatisfy(ref -> assertThat(ref.contentHash()).matches("[0-9a-f]{64}"));
        }
        verify(agents,never()).getOnlineOriginalText(any(),any());
        assertThat(assignment.getExecutionId()).isEqualTo(71L);
    }
    @Test void executionFailureIsTerminalFailureWithoutTurningHealthyLedgerIntoFailure() {
        when(tasks.reportFacts(any())).thenReturn(Result.ok(List.of(task("FAILED",usage))));
        when(onlineAgents.reportFacts(any())).thenReturn(Result.ok(List.of(execution("71","FAILED"))));
        when(agents.queryEvaluationEvidence(any())).thenReturn(Result.ok(List.of(audit("FAILED"))));
        assertThat(service.evaluate(OnlineRuleContractValidator.TASK,"{}","{}",assignment).metrics().getFirst().value().booleanValue()).isFalse();
        assertThat(service.evaluate(OnlineRuleContractValidator.AUDIT,"{}","{}",assignment).status().name()).isEqualTo("PASSED");
    }
    @Test void unavailablePermissionStopsBeforeUnscopedAuditProjection() {
        when(tasks.reportFacts(any())).thenReturn(Result.fail(40300,"denied"));
        assertThatThrownBy(() -> service.evaluate(OnlineRuleContractValidator.TASK,"{}","{}",assignment)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(agents,onlineAgents);
    }
    @Test void differentExecutionCannotSupplyTraceOrMetrics() {
        when(onlineAgents.reportFacts(any())).thenReturn(Result.ok(List.of(execution("72","COMPLETED"))));
        assertThatThrownBy(() -> service.evaluate(OnlineRuleContractValidator.AUDIT,"{}","{}",assignment)).hasMessageContaining("ONLINE_EVALUATION_IDENTITY_CONFLICT");
        verifyNoInteractions(agents);
    }
    @Test void unknownLedgerRemainsUnavailableWhileTerminalRuleCanStillRun() {
        when(tasks.reportFacts(any())).thenReturn(Result.ok(List.of(task("COMPLETED",null))));
        assertThatThrownBy(() -> service.evaluate(OnlineRuleContractValidator.AUDIT,"{}","{}",assignment)).hasMessageContaining("ONLINE_FACT_UNKNOWN");
        assertThat(service.evaluate(OnlineRuleContractValidator.TASK,"{}","{}",assignment).status().name()).isEqualTo("PASSED");
    }
    @Test void unfinishedOrCanceledTaskProducesNoFailureMetricForEitherOnlineFactRule() {
        for (String status : List.of("PENDING","DISPATCHED","RUNNING","WAITING_INPUT","WAITING_AUTH","CANCELING","UNKNOWN")) {
            when(tasks.reportFacts(any())).thenReturn(Result.ok(List.of(task(status,usage))));
            for (String key : List.of(OnlineRuleContractValidator.TASK,OnlineRuleContractValidator.AUDIT)) {
                var result = service.evaluate(key,"{}","{}",assignment);
                assertThat(result.status().name()).as(status + ":" + key).isEqualTo("SKIPPED");
                assertThat(result.summaryCode()).isEqualTo("ONLINE_EVALUATION_SUBJECT_NOT_READY");
                assertThat(result.metrics()).isEmpty();
            }
        }
    }
    @Test void completedLocalTaskCannotHideUnfinishedOrCanceledExecution() {
        for (String status : List.of("SUBMITTED","WORKING","INPUT_REQUIRED","AUTH_REQUIRED","CANCELED","UNKNOWN")) {
            when(onlineAgents.reportFacts(any())).thenReturn(Result.ok(List.of(execution("71",status))));
            when(agents.queryEvaluationEvidence(any())).thenReturn(Result.ok(List.of(audit(status))));
            var result = service.evaluate(OnlineRuleContractValidator.TASK,"{}","{}",assignment);
            assertThat(result.status().name()).as(status).isEqualTo("SKIPPED");
            assertThat(result.metrics()).isEmpty();
        }
    }
    @Test void definiteExecutionFailureIsRetainedEvenBeforeLocalTaskWriteback() {
        when(tasks.reportFacts(any())).thenReturn(Result.ok(List.of(task("CANCELING",usage))));
        when(onlineAgents.reportFacts(any())).thenReturn(Result.ok(List.of(execution("71","FAILED"))));
        when(agents.queryEvaluationEvidence(any())).thenReturn(Result.ok(List.of(audit("FAILED"))));
        assertThat(service.evaluate(OnlineRuleContractValidator.TASK,"{}","{}",assignment).status().name()).isEqualTo("FAILED");
        assertThat(service.evaluate(OnlineRuleContractValidator.AUDIT,"{}","{}",assignment).metrics()).isEmpty();
    }
    @Test void confirmedCancellationIsMissingForDeliveryButCanHaveAHealthyAuditLedger() {
        when(tasks.reportFacts(any())).thenReturn(Result.ok(List.of(task("TERMINATED",usage))));
        when(onlineAgents.reportFacts(any())).thenReturn(Result.ok(List.of(execution("71","CANCELED"))));
        when(agents.queryEvaluationEvidence(any())).thenReturn(Result.ok(List.of(audit("CANCELED"))));
        assertThat(service.evaluate(OnlineRuleContractValidator.TASK,"{}","{}",assignment).metrics()).isEmpty();
        assertThat(service.evaluate(OnlineRuleContractValidator.AUDIT,"{}","{}",assignment).status().name()).isEqualTo("PASSED");
    }
}
