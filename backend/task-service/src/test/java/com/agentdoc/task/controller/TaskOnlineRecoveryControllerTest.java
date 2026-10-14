package com.agentdoc.task.controller;

import com.agentdoc.task.enums.TaskRecoveryReason;
import com.agentdoc.task.pojo.vo.TaskRecoveryStatusVO;
import com.agentdoc.task.service.TaskOnlineTerminalRepairService;
import com.agentdoc.task.service.TaskRecoveryService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskOnlineRecoveryControllerTest {
    @ParameterizedTest
    @EnumSource(value = TaskRecoveryReason.class, names = {"CAPABILITY_EXPIRED", "SOURCE_CAPABILITY_INVALID"})
    void expiredOrInvalidProofStillUsesOriginalRecoveryPolicy(TaskRecoveryReason reason) {
        var recovery = mock(TaskRecoveryService.class); var online = mock(TaskOnlineTerminalRepairService.class);
        var controller = new TaskRecoveryController(recovery, online);
        var status = new TaskRecoveryStatusVO(10L, 8, true, true, reason, null);
        when(recovery.status(10L)).thenReturn(status); when(recovery.recoverManually(10L)).thenReturn(status);
        assertThat(controller.status(10L).data().reason()).isEqualTo(reason);
        assertThat(controller.recover(10L, null).data().reason()).isEqualTo(reason);
        verify(recovery).recoverManually(10L); verifyNoInteractions(online);
    }
    @Test void validOriginalProofCanUseNoStagingTerminalRepair() {
        var recovery = mock(TaskRecoveryService.class); var online = mock(TaskOnlineTerminalRepairService.class);
        var controller = new TaskRecoveryController(recovery, online);
        when(recovery.status(10L)).thenReturn(new TaskRecoveryStatusVO(10L, 8, true, false, TaskRecoveryReason.NOT_REQUIRED, null));
        when(online.pending(10L)).thenReturn(true); when(online.repair(10L)).thenReturn(true);
        assertThat(controller.status(10L).data().reason()).isEqualTo(TaskRecoveryReason.ONLINE_TERMINAL_FINALIZATION_PENDING);
        controller.recover(10L, null); verify(online).repair(10L); verify(recovery, never()).recoverManually(any());
    }
}
