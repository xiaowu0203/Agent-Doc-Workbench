package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.SpacePermissionConstant;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.TaskRecoveryAuthFeign;
import com.agentdoc.common.feign.TaskRecoveryDocumentFeign;
import com.agentdoc.common.feign.dto.TaskRecoveryIssueDTO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import com.agentdoc.common.feign.vo.TaskRecoveryRemoteVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.a2a.A2aTaskSynchronizationService;
import com.agentdoc.task.a2a.TaskRecoveryClient;
import com.agentdoc.task.config.TaskRecoveryProperties;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.TaskRecoveryReason;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.vo.TaskRecoveryEventVO;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskRecoveryServiceTest {
    private final TaskMapper mapper = mock(TaskMapper.class);
    private final TaskCapabilityCryptoService crypto = mock(TaskCapabilityCryptoService.class);
    private final TaskRecoveryProperties properties = new TaskRecoveryProperties();
    private final TaskRecoveryAuthFeign auth = mock(TaskRecoveryAuthFeign.class);
    private final TaskRecoveryDocumentFeign drafts = mock(TaskRecoveryDocumentFeign.class);
    private final TaskRecoveryClient client = mock(TaskRecoveryClient.class);
    private final A2aTaskSynchronizationService sync = mock(A2aTaskSynchronizationService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final DocumentFeign documents = mock(DocumentFeign.class);
    private final RedisUtils redis = mock(RedisUtils.class);
    private final TaskRecoveryService service = new TaskRecoveryService(mapper, crypto, properties, auth, drafts, client, sync, audit, documents, redis);
    private TaskEntity task;

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
        properties.setMachineKey("test-only-".repeat(4));
        task = new TaskEntity();
        task.setId(1L);
        task.setAgentId(2L);
        task.setSpaceId(3L);
        task.setDocumentId(4L);
        task.setDocumentType(DocType.DRAFT.getCode());
        task.setStatus(TaskStatus.RUNNING.getCode());
        task.setExecutionMode("LIVE");
        task.setDocumentVersionSnapshot(1L);
        task.setDocumentContentSha256("a".repeat(64));
        task.setInputSnapshotSchemaVersion(1);
        task.setInputSnapshotHash("b".repeat(64));
        task.setAgentExecutionId(5L);
        task.setA2aTaskId("remote");
        task.setA2aContextId("context");
        task.setCapabilityToken("encrypted-proof");
        when(mapper.selectById(1L)).thenReturn(task);
        when(crypto.decrypt("encrypted-proof")).thenReturn(proof(-120));
        when(auth.issueRecovery(anyString(), any())).thenReturn(Result.ok("query-capability"));
        when(auth.issueFinalization(anyString(), any())).thenReturn(Result.ok("draft-capability"));
        when(drafts.finalizeDraft(1L, "draft-capability")).thenReturn(Result.ok());
        when(client.query("remote", "query-capability", false)).thenReturn(remote(TaskState.TASK_STATE_COMPLETED));
        when(sync.synchronizeRecovered(eq(task), any(), any(), any())).thenReturn(true);
        when(redis.setIfAbsent(anyString(), any(), any())).thenReturn(true);
    }

    @Test
    void completedDraftUsesSeparateCredentialsAndSameRecoveryIdBeforeLocalWritebackWithoutOverwritingOriginalProof() {
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERED);
        ArgumentCaptor<TaskRecoveryIssueDTO> requests = ArgumentCaptor.forClass(TaskRecoveryIssueDTO.class);
        verify(auth).issueRecovery(eq(properties.getMachineKey()), requests.capture());
        verify(auth).issueFinalization(eq(properties.getMachineKey()), requests.capture());
        assertThat(requests.getAllValues().get(0).cancelRequested()).isFalse();
        assertThat(requests.getAllValues().get(1).remoteTerminalStatus()).isEqualTo("COMPLETED");
        assertThat(requests.getAllValues().get(0).recoveryId()).isEqualTo(requests.getAllValues().get(1).recoveryId());
        assertThat(task.getCapabilityToken()).isEqualTo("encrypted-proof");
        var order = inOrder(client, auth, drafts, sync);
        order.verify(client).query("remote", "query-capability", false);
        order.verify(auth).issueFinalization(anyString(), any());
        order.verify(drafts).finalizeDraft(1L, "draft-capability");
        order.verify(sync).synchronizeRecovered(eq(task), any(), any(), any());
        ArgumentCaptor<TaskRecoveryEventVO> events = ArgumentCaptor.forClass(TaskRecoveryEventVO.class);
        verify(audit, atLeastOnce()).recordRecovery(eq(3L), eq(1L), any(), events.capture());
        assertThat(JsonUtils.toJson(events.getAllValues())).doesNotContain("query-capability", "draft-capability", "encrypted-proof", properties.getMachineKey());
    }

    @Test
    void failedAndCanceledTasksUseDiscardNotFinalizeAndNeverInventCancellation() {
        when(client.query("remote", "query-capability", false)).thenReturn(remote(TaskState.TASK_STATE_FAILED));
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERED);
        verify(client, never()).query(any(), any(), eq(true));
        verify(auth).issueFinalization(anyString(), argThat(request -> "FAILED".equals(request.remoteTerminalStatus())));
        verify(audit).recordRecovery(eq(3L), eq(1L), eq(AuditAction.TASK_RECOVERY_ACTION), argThat(event -> "DISCARD".equals(event.action())));
    }

    @Test
    void onlyRecordedCancelingRequestsCancellationAndAlreadyFinalRemoteSkipsCancelRpc() {
        task.setStatus(TaskStatus.CANCELING.getCode());
        when(client.query("remote", "query-capability", false)).thenReturn(remote(TaskState.TASK_STATE_WORKING));
        when(client.query("remote", "query-capability", true)).thenReturn(remote(TaskState.TASK_STATE_CANCELED));
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERED);
        verify(auth).issueRecovery(anyString(), argThat(TaskRecoveryIssueDTO::cancelRequested));
        verify(client).query("remote", "query-capability", true);
        verify(auth).issueFinalization(anyString(), argThat(request -> "CANCELED".equals(request.remoteTerminalStatus())));
    }

    @Test
    void runningRemoteRefreshesActivityButDoesNotFinalizeOrForgeTerminalState() {
        when(client.query("remote", "query-capability", false)).thenReturn(remote(TaskState.TASK_STATE_WORKING));
        assertThat(run()).isEqualTo(TaskRecoveryReason.REMOTE_TASK_ACTIVE);
        verify(sync).synchronizeRecovered(eq(task), any(), any(), any());
        verifyNoInteractions(drafts);
        verify(auth, never()).issueFinalization(any(), any());
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING.getCode());
    }

    @Test
    void isolatedOrFormalTasksNeverTouchDraftEndpoint() {
        task.setExecutionMode("ISOLATED");
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERED);
        task.setExecutionMode("LIVE");
        task.setDocumentType(DocType.FORMAL.getCode());
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERED);
        verifyNoInteractions(drafts);
        verify(auth, never()).issueFinalization(any(), any());
    }

    @Test
    void unconfiguredInvalidSourceAndUnavailableRemoteLeaveTaskActiveWithStructuredReasons() {
        properties.setMachineKey("");
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERY_SERVICE_UNCONFIGURED);
        verifyNoInteractions(auth, client, drafts, sync);
        properties.setMachineKey("test-only-".repeat(4));
        when(auth.issueRecovery(anyString(), any())).thenReturn(Result.fail(ErrorCode.FORBIDDEN, "SOURCE_CAPABILITY_INVALID"));
        assertThat(run()).isEqualTo(TaskRecoveryReason.SOURCE_CAPABILITY_INVALID);
        when(auth.issueRecovery(anyString(), any())).thenReturn(Result.ok("query-capability"));
        when(client.query(any(), any(), anyBoolean())).thenThrow(new IllegalStateException("transport error"));
        assertThat(run()).isEqualTo(TaskRecoveryReason.REMOTE_TASK_UNAVAILABLE);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING.getCode());
        verifyNoInteractions(drafts, sync);
    }

    @Test
    void draftConflictOrMissingAuthoritativeLedgerBlocksLocalTerminalWrite() {
        when(drafts.finalizeDraft(1L, "draft-capability")).thenReturn(Result.fail(ErrorCode.CONFLICT, "DRAFT_VERSION_CONFLICT"));
        assertThat(run()).isEqualTo(TaskRecoveryReason.DRAFT_VERSION_CONFLICT);
        verifyNoInteractions(sync);
        when(client.query("remote", "query-capability", false)).thenReturn(new TaskRecoveryRemoteVO<>(remote(TaskState.TASK_STATE_COMPLETED).remoteTask(), null));
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERY_WRITEBACK_FAILED);
        verify(drafts, times(1)).finalizeDraft(any(), any());
    }

    @Test
    void resourceOrExecutionMismatchStopsBeforeDraftSideEffects() {
        task.setAgentExecutionId(99L);
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
        task.setAgentExecutionId(5L);
        task.setA2aContextId("different");
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERY_IDENTITY_MISMATCH);
        verifyNoInteractions(drafts, sync);
    }

    @Test
    void losingLockAfterRemoteQueryDoesNotFinalizeOrCommit() {
        AtomicBoolean owner = new AtomicBoolean(true);
        when(client.query(any(), any(), anyBoolean())).thenAnswer(call -> { owner.set(false); return remote(TaskState.TASK_STATE_COMPLETED); });
        assertThat(service.recoverLocked(task, owner::get, null)).isEqualTo(TaskRecoveryReason.RECOVERY_CAPACITY_EXCEEDED);
        verifyNoInteractions(drafts, sync);
    }

    @Test
    void alreadyFinalLocalTaskAndUnexpiredCapabilityDoNotIssueAnything() {
        task.setStatus(TaskStatus.COMPLETED.getCode());
        assertThat(run()).isEqualTo(TaskRecoveryReason.NOT_REQUIRED);
        task.setStatus(TaskStatus.RUNNING.getCode());
        when(crypto.decrypt("encrypted-proof")).thenReturn(proof(3600));
        assertThat(run()).isEqualTo(TaskRecoveryReason.NOT_REQUIRED);
        verifyNoInteractions(auth, client, drafts, sync);
    }

    @Test
    void manualPermissionsAreCheckedBeforeLockIssuanceAndExternalEffectsWhileDiagnosticsOnlyRequireRead() {
        try (var user = mockStatic(AuthUtils.class)) {
            user.when(AuthUtils::getUserIdOrException).thenReturn(10L);
            when(documents.checkSpacePermission(3L, SpacePermissionConstant.TASK_READ)).thenReturn(Result.ok());
            when(documents.checkSpacePermission(3L, SpacePermissionConstant.TASK_TERMINATE)).thenReturn(Result.fail(ErrorCode.FORBIDDEN));
            assertThatThrownBy(() -> service.recoverManually(1L)).isInstanceOf(BusinessException.class);
            when(documents.checkSpacePermission(3L, SpacePermissionConstant.TASK_TERMINATE)).thenReturn(Result.ok());
            when(documents.checkSpacePermission(3L, SpacePermissionConstant.DOCUMENT_EDIT)).thenReturn(Result.fail(ErrorCode.FORBIDDEN));
            assertThatThrownBy(() -> service.recoverManually(1L)).isInstanceOf(BusinessException.class);
            assertThat(service.status(1L).recoveryRequired()).isTrue();
            verifyNoInteractions(auth, client, drafts, sync, redis);
        }
    }

    @Test
    void repeatedSameFailureLogsOnlyOneFirstAlertWithinWindow() {
        properties.setMachineKey("");
        when(redis.setIfAbsent(contains(":first"), any(), any())).thenReturn(true, false);
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERY_SERVICE_UNCONFIGURED);
        assertThat(run()).isEqualTo(TaskRecoveryReason.RECOVERY_SERVICE_UNCONFIGURED);
        verify(audit, times(1)).recordRecovery(eq(3L), eq(1L), eq(AuditAction.TASK_RECOVERY_ALERT), any());
    }

    private TaskRecoveryReason run() { return service.recoverLocked(task, () -> true, null); }

    private TaskRecoveryRemoteVO<Task> remote(TaskState state) {
        Task remote = Task.builder().id("remote").contextId("context").status(new org.a2aproject.sdk.spec.TaskStatus(state)).build();
        return new TaskRecoveryRemoteVO<>(remote, new AgentExecutionTokenUsageVO(5L, 6L, 1L, BigDecimal.ZERO,
                BigDecimal.ZERO, "CNY", 1, LocalDateTime.now(), 10L, false, 0L, false, 20L, false));
    }

    private String proof(long secondsFromNow) {
        String payload = "{\"exp\":" + Instant.now().plusSeconds(secondsFromNow).getEpochSecond() + "}";
        return "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
}
