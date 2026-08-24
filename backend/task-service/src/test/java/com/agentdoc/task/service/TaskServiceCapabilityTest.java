package com.agentdoc.task.service;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.security.McpConfigCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceCapabilityTest {

    private static final long TASK_ID = 1L;
    private static final long AGENT_ID = 2L;
    private static final long SPACE_ID = 3L;
    private static final long DOCUMENT_ID = 4L;
    private static final String TOKEN = "task-capability";

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private AgentService agentService;
    @Mock
    private DocumentFeign documentFeign;
    @Mock
    private TaskMessagePublisher messagePublisher;
    @Mock
    private McpConfigCryptoService cryptoService;
    @Mock
    private AuthFeign authFeign;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TaskCapabilityVerifier taskCapabilityVerifier;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskMapper, agentService, documentFeign, messagePublisher, cryptoService,
                authFeign, auditLogService, objectMapper, taskCapabilityVerifier);
    }

    @Test
    void acceptsMatchingCapabilityForRunningTask() {
        when(taskCapabilityVerifier.verify(TOKEN)).thenReturn(capability(TASK_ID));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(TaskStatus.RUNNING));

        assertDoesNotThrow(() -> service.checkCapability(TASK_ID, TOKEN));
    }

    @Test
    void rejectsCapabilityForStoppedTask() {
        when(taskCapabilityVerifier.verify(TOKEN)).thenReturn(capability(TASK_ID));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(TaskStatus.TERMINATED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkCapability(TASK_ID, TOKEN));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    void rejectsCapabilityForAnotherTask() {
        when(taskCapabilityVerifier.verify(TOKEN)).thenReturn(capability(99L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.checkCapability(TASK_ID, TOKEN));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
        verify(taskMapper, never()).selectById(TASK_ID);
    }

    private Jwt capability(Long taskId) {
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .claim(JwtConstant.CLAIM_TASK_ID, String.valueOf(taskId))
                .claim(JwtConstant.CLAIM_AGENT_ID, String.valueOf(AGENT_ID))
                .claim(JwtConstant.CLAIM_SPACE_ID, String.valueOf(SPACE_ID))
                .claim(JwtConstant.CLAIM_DOCUMENT_ID, String.valueOf(DOCUMENT_ID))
                .build();
    }

    private TaskEntity task(TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setId(TASK_ID);
        task.setAgentId(AGENT_ID);
        task.setSpaceId(SPACE_ID);
        task.setDocumentId(DOCUMENT_ID);
        task.setStatus(status.getCode());
        return task;
    }
}
