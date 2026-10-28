package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.feign.vo.AgentToolCallVO;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.task.pojo.param.TaskToolCallSearchParam;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.TaskOutputType;
import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.vo.TaskExecutionDetailVO;
import com.agentdoc.task.pojo.vo.TaskVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskExecutionQueryServiceTest {

    private final TaskService taskService = mock(TaskService.class);
    private final AgentFeign agentFeign = mock(AgentFeign.class);
    private final DocumentFeign documentFeign = mock(DocumentFeign.class);
    private final ChangeRequestMapper changeRequestMapper = mock(ChangeRequestMapper.class);
    private final TaskExecutionQueryService service = new TaskExecutionQueryService(
            taskService, agentFeign, documentFeign, changeRequestMapper);

    @Test void independentToolPageEnrichesOnlyReturnedTasksAndKeepsMissingTaskIdentity() {
        var param = new TaskToolCallSearchParam(); param.setSpaceId(7L); param.setStartAt(LocalDateTime.of(2026, 10, 1, 0, 0)); param.setEndAt(param.getStartAt().plusDays(1)); param.setPageNum(2);
        when(documentFeign.checkSpacePermission(eq(7L), any())).thenReturn(Result.ok());
        when(agentFeign.searchToolCalls(any())).thenReturn(Result.ok(new PageVO<>(List.of(new AgentToolCallVO(9007199254740993L, null)), 31, 2, 10)));
        when(taskService.readListItems(7L, List.of(9007199254740993L))).thenReturn(List.of());
        var result = service.searchToolCalls(param);
        assertThat(result.total()).isEqualTo(31); assertThat(result.records().getFirst().taskId()).isEqualTo(9007199254740993L); assertThat(result.records().getFirst().task()).isNull();
        verify(taskService).readListItems(7L, List.of(9007199254740993L)); verifyNoInteractions(changeRequestMapper);
        when(documentFeign.checkSpacePermission(7L, "usage:read")).thenReturn(Result.fail(403, "撤权"));
        clearInvocations(agentFeign, taskService);
        assertThatThrownBy(() -> service.searchToolCalls(param)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(agentFeign, taskService);
    }

    @Test
    void usesCurrentAgentNameWhileExecutionIsStillPending() {
        TaskVO task = task(DocType.FORMAL, TaskStatus.PENDING, null);
        when(taskService.detail(11L)).thenReturn(task);
        when(agentFeign.getExecutionAudit(11L, 7L)).thenReturn(Result.ok(null));
        when(agentFeign.queryAgentRefs(any())).thenReturn(Result.ok(List.of(
                new AgentRefVO(9L, 7L, "当前 Agent"))));
        when(changeRequestMapper.selectOne(any())).thenReturn(null);

        TaskExecutionDetailVO result = service.detail(11L);

        assertThat(result.agentName()).isEqualTo("当前 Agent");
        assertThat(result.execution()).isNull();
        assertThat(result.output()).isNull();
    }

    @Test
    void returnsFrozenAgentNameAndChangeRequestOutput() {
        TaskVO task = task(DocType.FORMAL, TaskStatus.COMPLETED, "完成");
        AgentExecutionAuditVO audit = audit("执行时 Agent");
        ChangeRequestEntity request = new ChangeRequestEntity();
        request.setId(31L);
        request.setDocumentId(13L);
        request.setStatus(ChangeRequestStatus.PENDING.getCode());
        when(taskService.detail(11L)).thenReturn(task);
        when(agentFeign.getExecutionAudit(11L, 7L)).thenReturn(Result.ok(audit));
        when(changeRequestMapper.selectOne(any())).thenReturn(request);

        TaskExecutionDetailVO result = service.detail(11L);

        assertThat(result.agentName()).isEqualTo("执行时 Agent");
        assertThat(result.tokensEstimated()).isTrue();
        assertThat(result.output().type()).isEqualTo(TaskOutputType.CHANGE_REQUEST);
        assertThat(result.output().id()).isEqualTo(31L);
        assertThat(result.output().status()).isEqualTo("PENDING");
    }

    private TaskVO task(DocType type, TaskStatus status, String resultSummary) {
        return new TaskVO(null, 11L, com.agentdoc.task.enums.TaskLineageType.ORIGINAL,
                com.agentdoc.common.enums.TaskExecutionMode.LIVE,
                11L, "T-11", 7L, 9L, 13L, type, "审计任务", "检查文档", status,
                5000L, TaskReadScope.FULL, List.of(), 120L, true,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                0, null, resultSummary, 2L, LocalDateTime.now(), null, null, null, null, null);
    }

    private AgentExecutionAuditVO audit(String agentName) {
        return new AgentExecutionAuditVO(3L, 11L, 7L, 9L, agentName, 4L, 12, 300,
                "COMPLETED", false, "prompt", "snapshot", 2, null, null, List.of(), List.of(),
                List.of(), List.of(), 100L, false, 0L, false, 20L, false,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null);
    }
}
