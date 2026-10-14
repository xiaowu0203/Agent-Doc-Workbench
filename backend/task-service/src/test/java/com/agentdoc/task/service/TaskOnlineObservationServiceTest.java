package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.security.OnlineCapabilityVerifier;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import java.math.BigInteger;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskOnlineObservationServiceTest {
    private final TaskMapper tasks = mock(TaskMapper.class);
    private final TokenUsageDetailMapper ledger = mock(TokenUsageDetailMapper.class);
    private final OnlineEvaluationFeign evaluation = mock(OnlineEvaluationFeign.class);
    private final OnlineDocumentFeign document = mock(OnlineDocumentFeign.class);
    private final OnlineCapabilityVerifier verifier = mock(OnlineCapabilityVerifier.class);
    private final OnlineTaskBindingDTO binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000,
            "50", "10", "20", "30", "40", "1", "b".repeat(64), 1, "c".repeat(64), "71", 2, "d".repeat(64),
            "e".repeat(64), "f".repeat(64), "100", 600, "LIVE", "ORIGINAL", 2);
    private TaskOnlineObservationService service;
    private TaskEntity row;
    @BeforeEach void setup() {
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("verifier", verifier);
        service = new TaskOnlineObservationService(tasks, ledger, evaluation, document, beans.getBeanProvider(OnlineCapabilityVerifier.class));
        row = new TaskEntity(); row.setId(10L); row.setSpaceId(20L); row.setAgentId(30L); row.setDocumentId(40L); row.setCreatedBy(50L);
        row.setOnlineExperimentId(11L); row.setOnlineAssignmentId(61L); row.setOnlineBindingSchemaVersion(2);
        row.setOnlineBindingHash(OnlineProtocolUtils.hash("online.binding", binding)); row.setInputSnapshotHash(binding.inputHash());
        row.setDocumentVersionSnapshot(1L); row.setDocumentContentSha256(binding.documentContentHash()); row.setTokenBudget(100L);
        row.setExecutionMode("LIVE"); row.setLineageType("ORIGINAL"); row.setStatus(TaskStatus.TERMINATED.getCode());
        when(evaluation.scopedBindings(eq("11"), any(), eq("proof"), eq(List.of("10")))).thenReturn(Result.ok(List.of(binding)));
        when(document.scopedProtectionPermission(eq("11"), any(), eq("proof"), eq(List.of("10")))).thenReturn(Result.ok());
        when(tasks.selectList(any())).thenReturn(List.of(row)); when(ledger.selectList(any())).thenReturn(List.of());
    }
    @Test void absentLedgerRemainsUnknownAndLocalTerminalDoesNotClaimAgentTerminal() {
        var fact = service.facts("11", "proof", List.of("10")).getFirst();
        assertThat(fact.taskStatus()).isEqualTo("TERMINATED"); assertThat(fact.ledgerTokens()).isNull(); assertThat(fact.executionStatus()).isNull();
        assertThat(fact.neverDispatched()).isTrue(); verify(verifier).verify("proof", OnlineCapabilityPurpose.ONLINE_OBSERVE, "11", List.of("10"));
    }
    @Test void exactHistoricalAccountCanExceedLongAndCannotBeReplacedByAnotherExecution() {
        row.setAgentExecutionId(81L); var account = new TokenUsageDetailEntity(); account.setTaskId(10L); account.setSpaceId(20L); account.setAgentId(30L);
        account.setExecutionId(81L); account.setInputTokens(Long.MAX_VALUE); account.setOutputTokens(Long.MAX_VALUE);
        when(ledger.selectList(any())).thenReturn(List.of(account));
        assertThat(service.facts("11", "proof", List.of("10")).getFirst().ledgerTokens()).isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).toString());
        account.setExecutionId(82L); assertThatThrownBy(() -> service.facts("11", "proof", List.of("10"))).isInstanceOf(BusinessException.class);
    }
    @Test void currentPermissionLossOrTamperedFrozenBudgetRejectsFactsAndCancellationBeforeWrites() {
        row.setTokenBudget(101L); assertThatThrownBy(() -> service.cancel("11", "proof", List.of("10"))).isInstanceOf(BusinessException.class);
        row.setTokenBudget(100L); when(document.scopedProtectionPermission(anyString(), any(), anyString(), anyList())).thenReturn(Result.fail(403, "revoked"));
        assertThatThrownBy(() -> service.cancel("11", "proof", List.of("10"))).isInstanceOf(BusinessException.class); verify(tasks, never()).update(any(), any());
    }
}
