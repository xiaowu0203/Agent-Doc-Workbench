package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.*;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.*;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.a2a.TaskTerminalPersistenceService;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;

class TaskOnlineTerminalRepairServiceTest {
    private final TaskMapper tasks = mock(TaskMapper.class);
    private final OnlineEvaluationFeign evaluation = mock(OnlineEvaluationFeign.class);
    private final AgentFeign ledger = mock(AgentFeign.class);
    private final OnlineAgentFeign agents = mock(OnlineAgentFeign.class);
    private final OnlineDocumentFeign docs = mock(OnlineDocumentFeign.class);
    private final TaskCapabilityVerifier verifier = mock(TaskCapabilityVerifier.class);
    private final TaskCapabilityCryptoService crypto = mock(TaskCapabilityCryptoService.class);
    private final A2aTaskClient a2a = mock(A2aTaskClient.class);
    private final TaskTerminalPersistenceService persistence = mock(TaskTerminalPersistenceService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final RedisUtils redis = mock(RedisUtils.class);
    private final OnlineTaskBindingDTO binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000,
            "50", "10", "20", "30", "40", "0", "b".repeat(64), 1, "c".repeat(64), "71", 2, "d".repeat(64),
            "e".repeat(64), "f".repeat(64), "100", 600, "LIVE", "ORIGINAL", 2);
    private final String hash = OnlineProtocolUtils.hash("online.binding", binding);
    private final TaskEntity task = new TaskEntity();
    private final LocalDateTime finished = LocalDateTime.of(2026, 10, 13, 13, 15, 44);
    private final AgentExecutionTokenUsageVO usage = new AgentExecutionTokenUsageVO(80L, 90L, 1L, BigDecimal.ONE,
            BigDecimal.ONE, "CNY", 1, finished.minusMinutes(1), 6000L, false, 0L, false, 258L, false);

    private TaskOnlineTerminalRepairService fixture(String status) {
        task.setId(10L); task.setOnlineExperimentId(11L); task.setOnlineAssignmentId(61L); task.setSpaceId(20L);
        task.setAgentId(30L); task.setDocumentId(40L); task.setCreatedBy(50L); task.setDocumentType(DocType.DRAFT.getCode());
        task.setOnlineBindingSchemaVersion(2); task.setOnlineBindingHash(hash); task.setInputSnapshotHash(binding.inputHash());
        task.setDocumentVersionSnapshot(0L); task.setDocumentContentSha256(binding.documentContentHash()); task.setTokenBudget(100L);
        task.setExecutionMode("LIVE"); task.setLineageType("ORIGINAL"); task.setStatus(TaskStatus.CANCELING.getCode());
        task.setAgentExecutionId(80L); task.setA2aTaskId("remote"); task.setA2aContextId("context");
        when(tasks.selectById(10L)).thenReturn(task);
        when(evaluation.humanBinding("10")).thenReturn(Result.ok(binding));
        when(evaluation.humanProof("11")).thenReturn(Result.ok(new OnlineAuthorizationProofVO("11", "20", "a".repeat(64), 2, "STOPPED", "50")));
        when(agents.reportFacts(any())).thenReturn(Result.ok(List.of(remote(status, 80L, usage))));
        when(ledger.getExecutionTokenUsage(10L)).thenReturn(Result.ok(usage));
        task.setCapabilityToken("encrypted"); task.setOnlineSlotGeneration(1L); task.setOnlineSlotPermitHash("1".repeat(64));
        when(crypto.decrypt("encrypted")).thenReturn("source");
        when(verifier.verify("source")).thenReturn(Jwt.withTokenValue("source").header("alg", "RS256")
                .claim(EXPERIMENT_ID,"11").claim(ASSIGNMENT_ID,"61").claim(BINDING_SCHEMA,2).claim(BINDING_HASH,hash)
                .claim(SLOT_GENERATION,1).claim(SLOT_PERMIT_HASH,"1".repeat(64)).build());
        TaskState state = switch(status) { case "FAILED" -> TaskState.TASK_STATE_FAILED; case "CANCELED" -> TaskState.TASK_STATE_CANCELED;
            case "RUNNING" -> TaskState.TASK_STATE_WORKING; default -> TaskState.TASK_STATE_COMPLETED; };
        when(a2a.getForReconciliation("remote", "source")).thenReturn(Task.builder().id("remote").contextId("context")
                .status(new org.a2aproject.sdk.spec.TaskStatus(state)).build());
        when(docs.requireNoDraftStaging("10")).thenReturn(Result.ok());
        when(redis.setIfAbsent(anyString(), anyString(), any())).thenAnswer(call -> {
            when(redis.get(call.getArgument(0))).thenReturn(call.getArgument(1)); return true;
        });
        when(persistence.persist(any(), any(), any())).thenAnswer(call -> {
            assertThat(((BooleanSupplier)call.getArgument(2)).getAsBoolean()).isTrue(); return true;
        });
        return new TaskOnlineTerminalRepairService(tasks, evaluation, ledger, agents, docs, verifier, crypto, a2a, persistence, audit, redis);
    }
    private OnlineReportExecutionFactVO remote(String status, long executionId, AgentExecutionTokenUsageVO tokens) {
        return new OnlineReportExecutionFactVO("10", "61", hash, "AVAILABLE", String.valueOf(executionId), status,
                null, null, finished.toString(), OnlineReportUsageVO.from(tokens), null, null);
    }
    private boolean repair(TaskOnlineTerminalRepairService service) {
        try (var user = mockStatic(AuthUtils.class)) {
            user.when(AuthUtils::getUserIdOrException).thenReturn(50L); return service.repair(10L);
        }
    }
    @ParameterizedTest @CsvSource({"COMPLETED,COMPLETED", "FAILED,FAILED", "CANCELED,TERMINATED"})
    void terminalTruthWinsLateCancellationAndKeepsFrozenLedger(String remoteStatus, TaskStatus expected) {
        var service = fixture(remoteStatus);
        assertThat(repair(service)).isTrue(); assertThat(task.getStatus()).isEqualTo(expected.getCode());
        assertThat(task.getEndTime()).isEqualTo(finished); assertThat(task.getTokensUsed()).isEqualTo(6258L);
        assertThat(repair(service)).isFalse();
        verify(persistence, times(1)).persist(any(), any(), any()); verify(docs, times(2)).requireNoDraftStaging("10");
    }
    @Test void activeExecutionCannotInventTerminal() {
        var service = fixture("RUNNING"); assertThatThrownBy(() -> repair(service)).hasMessageContaining("REMOTE_TASK_ACTIVE");
        verifyNoInteractions(persistence, a2a);
    }
    @Test void wrongExecutionCannotReachDocumentOrWriteback() {
        var service = fixture("COMPLETED"); when(agents.reportFacts(any())).thenReturn(Result.ok(List.of(remote("COMPLETED", 81L, usage))));
        assertThatThrownBy(() -> repair(service)).hasMessageContaining("RECOVERY_IDENTITY_MISMATCH"); verifyNoInteractions(persistence, a2a);
    }
    @Test void missingLedgerCannotBecomeZero() {
        var service = fixture("COMPLETED"); when(ledger.getExecutionTokenUsage(10L)).thenReturn(Result.ok());
        assertThatThrownBy(() -> repair(service)).hasMessageContaining("RECOVERY_WRITEBACK_FAILED"); verifyNoInteractions(persistence, a2a);
    }
    @Test void existingStagingIsNeverCommittedOrDiscardedByObservation() {
        var service = fixture("COMPLETED"); when(docs.requireNoDraftStaging("10")).thenReturn(Result.fail(ErrorCode.CONFLICT, "DRAFT_VERSION_CONFLICT"));
        assertThatThrownBy(() -> repair(service)).hasMessageContaining("DRAFT_VERSION_CONFLICT"); verifyNoInteractions(persistence);
    }
    @Test void currentOwnerRevocationStopsBeforeAgentFacts() {
        var service = fixture("COMPLETED"); when(evaluation.humanProof("11")).thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "OWNER_REQUIRED"));
        assertThatThrownBy(() -> repair(service)).hasMessageContaining("OWNER_REQUIRED"); verifyNoInteractions(agents, docs, persistence);
    }
    @Test void proofManifestOrAuthorCannotReplaceFrozenAuthority() {
        var service = fixture("COMPLETED");
        when(evaluation.humanProof("11")).thenReturn(Result.ok(new OnlineAuthorizationProofVO("11", "20", "f".repeat(64), 2, "STOPPED", "51")));
        assertThatThrownBy(() -> repair(service)).hasMessageContaining("RECOVERY_IDENTITY_MISMATCH");
        verifyNoInteractions(ledger, agents, docs, persistence);
    }
    @Test void lockLostBeforeWritebackCannotUpdateTask() {
        var service = fixture("COMPLETED"); when(docs.requireNoDraftStaging("10")).thenAnswer(call -> {
            when(redis.get(anyString())).thenReturn("other-owner"); return Result.ok();
        });
        assertThatThrownBy(() -> repair(service)).hasMessageContaining("RECOVERY_CAPACITY_EXCEEDED"); verifyNoInteractions(persistence);
    }
    @Test void expiredOriginalProofIsNotRenewedAndCannotReadA2aOrWriteBack() {
        var service = fixture("COMPLETED"); when(verifier.verify("source")).thenThrow(new JwtException("expired"));
        assertThatThrownBy(() -> repair(service)).hasMessageContaining("expired"); verifyNoInteractions(a2a, persistence);
    }
    @Test void a2aIdentityConflictCannotWriteEvenWithHealthyUserFacts() {
        var service = fixture("COMPLETED");
        when(a2a.getForReconciliation("remote", "source")).thenReturn(Task.builder().id("other").contextId("context")
                .status(new org.a2aproject.sdk.spec.TaskStatus(TaskState.TASK_STATE_COMPLETED)).build());
        assertThatThrownBy(() -> repair(service)).hasMessageContaining("RECOVERY_IDENTITY_MISMATCH"); verifyNoInteractions(persistence);
    }
}
