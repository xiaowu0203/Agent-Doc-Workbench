package com.agentdoc.task.a2a;

import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.feign.context.AuthorizationContext;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.agentdoc.task.service.TaskRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class A2aTaskReconciliationServiceTest {
    private final TaskMapper mapper = mock(TaskMapper.class);
    private final A2aTaskClient client = mock(A2aTaskClient.class);
    private final A2aTaskSynchronizationService sync = mock(A2aTaskSynchronizationService.class);
    private final TaskCapabilityCryptoService crypto = mock(TaskCapabilityCryptoService.class);
    private final RedisUtils redis = mock(RedisUtils.class);
    private final TaskRecoveryService recovery = mock(TaskRecoveryService.class);
    private final A2aTaskReconciliationService service = new A2aTaskReconciliationService(mapper, client, sync, crypto, redis, new A2aProperties(), recovery);
    private TaskEntity task;

    @BeforeEach
    void setup() {
        task = new TaskEntity();
        task.setId(1L);
        task.setStatus(TaskStatus.RUNNING.getCode());
        task.setA2aTaskId("remote");
        when(mapper.selectById(1L)).thenReturn(task);
        when(redis.setIfAbsent(anyString(), any(), any())).thenReturn(true);
    }

    @Test
    void expiredProofUsesDedicatedRecoveryWithOwnerTokenAndNeverNormalIdentityContexts() {
        when(recovery.hasExpiredProof(task)).thenReturn(true);
        service.reconcile(task);
        verify(recovery).recoverLocked(eq(task), any(), isNull());
        verifyNoInteractions(client, sync, crypto);
        assertThat(AuthorizationContext.current()).isNull();
        assertThat(TaskCapabilityContext.current()).isNull();
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(redis).setIfAbsent(anyString(), owner.capture(), any());
        assertThatCode(() -> UUID.fromString(owner.getValue())).doesNotThrowAnyException();
        verify(redis).deleteIfValueMatches(anyString(), eq(owner.getValue()));
        verify(redis, never()).delete(anyString());
    }

    @Test
    void takenOverLockIsDetectedAndCompareDeleteCannotRemoveNewOwner() {
        when(recovery.hasExpiredProof(task)).thenReturn(true);
        when(redis.get(anyString())).thenReturn("new-owner");
        service.reconcile(task);
        ArgumentCaptor<BooleanSupplier> owner = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(recovery).recoverLocked(eq(task), owner.capture(), isNull());
        assertThat(owner.getValue().getAsBoolean()).isFalse();
        verify(redis, never()).delete(anyString());
    }

    @Test
    void candidateAlreadyFinalAfterLockAcquisitionIsNotQueried() {
        task.setStatus(TaskStatus.COMPLETED.getCode());
        service.reconcile(task);
        verifyNoInteractions(recovery, client, sync, crypto);
        verify(redis).deleteIfValueMatches(anyString(), anyString());
    }

    @Test
    void failedLockAcquisitionDoesNotReleaseSomeoneElsesLockOrReadProof() {
        when(redis.setIfAbsent(anyString(), any(), any())).thenReturn(false);
        service.reconcile(task);
        verifyNoInteractions(mapper, recovery, client, sync, crypto);
        verify(redis, never()).deleteIfValueMatches(anyString(), anyString());
    }

    @Test
    void corruptProofStillProducesRecoveryDiagnosticsInsteadOfSilentlySkippingForever() {
        when(recovery.hasExpiredProof(task)).thenThrow(new IllegalArgumentException("bad encrypted proof"));
        service.reconcile(task);
        verify(recovery).recoverLocked(eq(task), any(), isNull());
        verifyNoInteractions(client, sync, crypto);
    }
}
