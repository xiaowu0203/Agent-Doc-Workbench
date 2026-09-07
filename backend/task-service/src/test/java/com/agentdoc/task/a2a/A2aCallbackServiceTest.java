package com.agentdoc.task.a2a;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.a2aproject.sdk.spec.Task;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2aCallbackServiceTest {

    private static final String CAPABILITY = "capability";
    private static final String A2A_TASK_ID = "a2a-task";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"task\":{\"id\":\"a2a-task\"}}",
            "{\"message\":{\"taskId\":\"a2a-task\"}}",
            "{\"statusUpdate\":{\"taskId\":\"a2a-task\"}}",
            "{\"artifactUpdate\":{\"taskId\":\"a2a-task\"}}"
    })
    void shouldSynchronizeA2aV1StreamingWrapper(String payload) throws Exception {
        TaskCapabilityVerifier capabilityVerifier = mock(TaskCapabilityVerifier.class);
        TaskService taskService = mock(TaskService.class);
        A2aTaskClient a2aTaskClient = mock(A2aTaskClient.class);
        A2aTaskSynchronizationService synchronizationService = mock(A2aTaskSynchronizationService.class);
        A2aCallbackService callbackService = new A2aCallbackService(
                capabilityVerifier, taskService, a2aTaskClient, synchronizationService);
        TaskEntity task = runningTask();
        Task remoteTask = mock(Task.class);
        JsonNode event = OBJECT_MAPPER.readTree(payload);

        when(capabilityVerifier.verify(CAPABILITY)).thenReturn(capability(task));
        when(taskService.require(task.getId())).thenReturn(task);
        when(a2aTaskClient.get(A2A_TASK_ID, CAPABILITY)).thenReturn(remoteTask);

        callbackService.receive(event, CAPABILITY);

        verify(a2aTaskClient).get(A2A_TASK_ID, CAPABILITY);
        verify(synchronizationService).synchronize(task, remoteTask);
    }

    private TaskEntity runningTask() {
        TaskEntity task = new TaskEntity();
        task.setId(10L);
        task.setAgentId(11L);
        task.setSpaceId(12L);
        task.setDocumentId(13L);
        task.setStatus(TaskStatus.RUNNING.getCode());
        return task;
    }

    private Jwt capability(TaskEntity task) {
        return Jwt.withTokenValue(CAPABILITY)
                .header("alg", "none")
                .claim(JwtConstant.CLAIM_TASK_ID, String.valueOf(task.getId()))
                .claim(JwtConstant.CLAIM_AGENT_ID, String.valueOf(task.getAgentId()))
                .claim(JwtConstant.CLAIM_SPACE_ID, String.valueOf(task.getSpaceId()))
                .claim(JwtConstant.CLAIM_DOCUMENT_ID, String.valueOf(task.getDocumentId()))
                .build();
    }
}
