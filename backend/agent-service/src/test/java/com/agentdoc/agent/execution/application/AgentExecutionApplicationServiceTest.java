package com.agentdoc.agent.execution.application;

import com.agentdoc.agent.service.OnlineAgentAdmissionService;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.execution.context.SkillExecutionSnapshot;
import com.agentdoc.common.feign.dto.OnlineDispatchIdentityDTO;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.enums.ErrorCode;
import java.util.List;
import com.agentdoc.agent.enums.AgentExecutionStatus;
import com.agentdoc.agent.execution.runtime.AgentExecutionRuntime;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.observability.AgentTelemetry;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;

class AgentExecutionApplicationServiceTest {
    @Test
    void admissionFailureBeforeRuntimeLeavesAuthoritativeZeroAndNeverCallsModel() {
        var mapper = mock(AgentExecutionMapper.class); var prepare = mock(ExecutionPreparationService.class);
        var persistence = mock(AgentExecutionPersistenceService.class); var admission = mock(OnlineAgentAdmissionService.class);
        var runtime = mock(AgentExecutionRuntime.class); var execution = new AgentExecutionEntity(); execution.setId(71L); execution.setStatus("SUBMITTED");
        var identity = new OnlineDispatchIdentityDTO("11", "61", 2, "a".repeat(64), 1L, "b".repeat(64));
        var input = new AgentTaskInputDTO(10L, 30L, 20L, 40L, 100L, "LIVE", 1L, "c".repeat(64), 1, "d".repeat(64),
                null, null, null, null, null, null, null, null, "http://task/mcp", "execution", identity);
        var context = mock(RequestContext.class); var emitter = mock(AgentEmitter.class);
        when(context.getMessage()).thenReturn(Message.builder().role(Message.Role.ROLE_USER).messageId("message").parts(new DataPart(input)).build());
        when(context.getTaskId()).thenReturn("remote"); when(context.getContextId()).thenReturn("context"); when(context.getUserInput()).thenReturn("new instruction");
        when(prepare.prepare(anyString(), anyString(), any(), anyString())).thenReturn(new ExecutionPreparationService.PreparedExecution(mock(AgentEntity.class),
                mock(ModelEntity.class), mock(SkillExecutionSnapshot.class), "frozen", execution, List.of()));
        doThrow(new BusinessException(ErrorCode.CONFLICT, "ONLINE_GATE_CLOSED")).when(admission).begin(any());
        new AgentExecutionApplicationService(mapper, prepare, persistence, new AgentTelemetry(), runtime, new ObjectMapper(), admission).execute(context, emitter);
        verify(persistence).markFailed(eq(execution), eq("ONLINE_GATE_CLOSED"), argThat(usage -> usage.input().value() == 0 && usage.output().value() == 0));
        verify(runtime, never()).execute(any(), any()); verify(runtime, never()).execute(any(), any(), any());
    }
    @Test
    void onlineCancelOnlyRequestsCancellationUntilRuntimeFinishes() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "online-cancel"), AgentExecutionEntity.class);
        var mapper = mock(AgentExecutionMapper.class);
        var persistence = mock(AgentExecutionPersistenceService.class);
        var runtime = mock(AgentExecutionRuntime.class);
        var execution = new AgentExecutionEntity(); execution.setId(71L); execution.setOnlineAssignmentId(61L); execution.setStatus("WORKING");
        when(mapper.selectOne(any())).thenReturn(execution);
        var service = new AgentExecutionApplicationService(mapper, mock(ExecutionPreparationService.class), persistence,
                new AgentTelemetry(), runtime, new ObjectMapper(), mock(OnlineAgentAdmissionService.class));
        var context = mock(RequestContext.class); when(context.getTaskId()).thenReturn("remote"); var emitter = mock(AgentEmitter.class);
        service.cancel(context, emitter);
        verify(mapper).update(any(), any());
        verify(persistence, never()).markCanceled(any());
        verify(emitter, never()).cancel(any(Message.class));
        verify(emitter).sendMessage(any(Message.class));
        assertThat(execution.getStatus()).isEqualTo("WORKING");
    }

    @Test
    void replaysConcurrentExecutionWhenUniqueInsertLosesRace() {
        AgentExecutionMapper mapper = mock(AgentExecutionMapper.class);
        ExecutionPreparationService preparationService = mock(ExecutionPreparationService.class);
        AgentExecutionPersistenceService persistenceService = mock(AgentExecutionPersistenceService.class);
        AgentExecutionRuntime runtime = mock(AgentExecutionRuntime.class);
        AgentTelemetry telemetry = new AgentTelemetry();
        RequestContext context = mock(RequestContext.class);
        AgentEmitter emitter = mock(AgentEmitter.class);
        AgentExecutionEntity concurrent = new AgentExecutionEntity();
        concurrent.setStatus(AgentExecutionStatus.SUBMITTED.name());
        AgentTaskInputDTO input = new AgentTaskInputDTO(11L, 22L, 33L, 44L, 100L,
                TaskExecutionMode.LIVE.name(), 5L, "a".repeat(64), 1, "b".repeat(64),
                null, null, null, null, null,
                null, null, null,
                "http://task-service/mcp", "capability", null);
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId("message-id")
                .parts(new DataPart(input))
                .build();
        when(context.getMessage()).thenReturn(message);
        when(context.getUserInput()).thenReturn("instruction");
        when(context.getTaskId()).thenReturn("a2a-task");
        when(context.getContextId()).thenReturn("a2a-context");
        when(mapper.selectOne(any())).thenReturn(null, concurrent);
        when(preparationService.prepare(anyString(), anyString(), any(), anyString()))
                .thenThrow(new DuplicateKeyException("duplicate workbench task"));
        AgentExecutionApplicationService service = new AgentExecutionApplicationService(
                mapper, preparationService, persistenceService, telemetry, runtime, new ObjectMapper(), mock(OnlineAgentAdmissionService.class));

        service.execute(context, emitter);

        verify(emitter).startWork(any(Message.class));
        verify(persistenceService, never()).markWorking(any());
        verify(runtime, never()).execute(any(), any());
    }
}
