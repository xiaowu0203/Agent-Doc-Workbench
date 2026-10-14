package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.dto.OnlineReportFactsQueryDTO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskOnlineReportFactsServiceTest {
    private final TaskMapper tasks = mock(TaskMapper.class);
    private final TokenUsageDetailMapper ledger = mock(TokenUsageDetailMapper.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private final TaskOnlineReportFactsService service = new TaskOnlineReportFactsService(tasks, ledger, documents);
    private final OnlineTaskBindingDTO binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000, "50", "10", "20", "30", "40", "1", "b".repeat(64), 1, "c".repeat(64), "71", 2, "d".repeat(64), "e".repeat(64), "f".repeat(64), "100", 600, "LIVE", "ORIGINAL", 2);
    private final OnlineReportFactsQueryDTO request = new OnlineReportFactsQueryDTO("20", "11", List.of(binding));
    private TaskEntity row() { var row = new TaskEntity(); row.setId(10L); row.setSpaceId(20L); row.setAgentId(30L); row.setDocumentId(40L); row.setCreatedBy(50L);
        row.setOnlineExperimentId(11L); row.setOnlineAssignmentId(61L); row.setOnlineBindingSchemaVersion(2); row.setOnlineBindingHash(OnlineProtocolUtils.hash("online.binding", binding));
        row.setInputSnapshotHash(binding.inputHash()); row.setDocumentVersionSnapshot(1L); row.setDocumentContentSha256(binding.documentContentHash()); row.setTokenBudget(100L); row.setExecutionMode("LIVE"); row.setLineageType("ORIGINAL"); row.setStatus(TaskStatus.COMPLETED.getCode()); row.setAgentExecutionId(81L); return row; }
    @Test void readsHistoricalLedgerInTwoBatchQueriesAndPreservesZeroAndEstimationAndPrice() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); when(documents.checkSpacePermission(eq(20L), anyString())).thenReturn(Result.ok()); when(tasks.selectList(any())).thenReturn(List.of(row()));
            var account = new TokenUsageDetailEntity(); account.setId(91L); account.setTaskId(10L); account.setExecutionId(81L); account.setSpaceId(20L); account.setAgentId(30L); account.setModelConfigVersion(7L);
            account.setInputPricePerMillion(new BigDecimal("1.2300")); account.setInputTokens(0L); account.setInputTokensEstimated(false); account.setOutputTokens(0L); account.setCreatedAt(LocalDateTime.of(2026, 10, 12, 10, 0));
            when(ledger.selectList(any())).thenReturn(List.of(account)); var fact = service.facts(request).getFirst(); assertThat(fact.usage().inputTokens()).isZero(); assertThat(fact.usage().inputTokensEstimated()).isFalse();
            assertThat(fact.usage().inputPricePerMillion()).isEqualTo("1.23"); assertThat(fact.usage().modelConfigVersion()).isEqualTo(7L); assertThat(fact.ledgerRecordedAt()).isEqualTo("2026-10-12T10:00");
            verify(tasks, times(1)).selectList(any()); verify(ledger, times(1)).selectList(any());
            account.setExecutionId(82L); assertThatThrownBy(() -> service.facts(request)).isInstanceOf(BusinessException.class);
        }
    }
    @Test void absentTaskAndLedgerStayAbsentAndCurrentPermissionIsRequiredBeforeQuery() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); when(documents.checkSpacePermission(eq(20L), anyString())).thenReturn(Result.ok()); when(tasks.selectList(any())).thenReturn(List.of()); when(ledger.selectList(any())).thenReturn(List.of());
            assertThat(service.facts(request).getFirst().state()).isEqualTo("ABSENT");
            clearInvocations(tasks, ledger); when(documents.checkSpacePermission(eq(20L), anyString())).thenReturn(Result.fail(403, "撤权")); assertThatThrownBy(() -> service.facts(request)).isInstanceOf(BusinessException.class); verifyNoInteractions(tasks, ledger);
        }
    }
    @Test void serviceCredentialCannotReplaceCurrentHumanAndDuplicateBindingsAreRejected() {
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenThrow(new BusinessException(401, "必须当前人类")); assertThatThrownBy(() -> service.facts(request)).isInstanceOf(BusinessException.class); verifyNoInteractions(tasks, ledger, documents);
            auth.when(AuthUtils::getUserIdOrException).thenReturn(502L); assertThatThrownBy(() -> new OnlineReportFactsQueryDTO("20", "11", List.of(binding, binding)).validate()).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
