package com.agentdoc.agent.execution.application;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionApplicationServiceTest {

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
                "http://task-service/mcp", "capability");
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
                mapper, preparationService, persistenceService, telemetry, runtime, new ObjectMapper());

        service.execute(context, emitter);

        verify(emitter).startWork(any(Message.class));
        verify(persistenceService, never()).markWorking(any());
        verify(runtime, never()).execute(any(), any());
    }
}
