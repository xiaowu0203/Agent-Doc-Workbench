package com.agentdoc.task.service;

import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.task.convertor.TaskTraceConvertor;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.enums.TaskTraceAvailability;
import com.agentdoc.task.infrastructure.JaegerTraceClient;
import com.agentdoc.task.pojo.vo.TaskVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TaskTraceQueryServiceTest {
    private final TaskService tasks = mock(TaskService.class);
    private final JaegerTraceClient client = mock(JaegerTraceClient.class);
    private final TaskTraceQueryService service = new TaskTraceQueryService(tasks, client, new TaskTraceConvertor());

    @Test
    void permissionDenialStopsBeforeJaeger() {
        when(tasks.detail(11L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权限"));
        assertThatThrownBy(() -> service.detail(11L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(client);
    }

    @Test
    void missingInvalidAndAllZeroTraceIdsNeverQueryBackend() {
        when(tasks.detail(11L)).thenReturn(task(null, 1), task("Bearer forbidden", 1), task("0".repeat(32), 1));
        assertThat(service.detail(11L).availabilityCode()).isEqualTo(TaskTraceAvailability.NO_TRACE);
        var invalid = service.detail(11L);
        assertThat(invalid.availabilityCode()).isEqualTo(TaskTraceAvailability.INVALID_TRACE_ID);
        assertThat(invalid.traceId()).isNull();
        assertThat(service.detail(11L).availabilityCode()).isEqualTo(TaskTraceAvailability.INVALID_TRACE_ID);
        verifyNoInteractions(client);
    }

    @Test
    void expiredWindowOnlyChangesMissingResultNotBackendError() {
        String trace = "a".repeat(32);
        when(tasks.detail(11L)).thenReturn(task(trace, 15));
        when(client.fetch(trace)).thenReturn(new JaegerTraceClient.Fetch(TaskTraceAvailability.NOT_FOUND_OR_NOT_SAMPLED, null),
                new JaegerTraceClient.Fetch(TaskTraceAvailability.BACKEND_UNAVAILABLE, null));
        assertThat(service.detail(11L).availabilityCode()).isEqualTo(TaskTraceAvailability.RETENTION_WINDOW_ELAPSED);
        var failed = service.detail(11L);
        assertThat(failed.availabilityCode()).isEqualTo(TaskTraceAvailability.BACKEND_UNAVAILABLE);
        assertThat(failed.spanCount()).isNull();
        assertThat(failed.taskId()).isEqualTo(11L);
    }

    @Test
    void recentMissingTraceDoesNotClaimSamplingOrDeletionAsFact() {
        String trace = "a".repeat(32);
        when(tasks.detail(11L)).thenReturn(task(trace, 1));
        when(client.fetch(trace)).thenReturn(new JaegerTraceClient.Fetch(TaskTraceAvailability.NOT_FOUND_OR_NOT_SAMPLED, null));
        assertThat(service.detail(11L).availabilityCode()).isEqualTo(TaskTraceAvailability.NOT_FOUND_OR_NOT_SAMPLED);
    }

    private static TaskVO task(String traceId, int days) {
        LocalDateTime time = LocalDateTime.now().minusDays(days);
        return new TaskVO(null, 11L, TaskLineageType.ORIGINAL, TaskExecutionMode.LIVE,
                11L, "T-11", 7L, 9L, 13L, DocType.FORMAL, "任务", "业务指令", TaskStatus.COMPLETED,
                1000L, TaskReadScope.FULL, List.of(), null, false, time, time, time, time,
                0, null, null, 2L, time, traceId);
    }
}
