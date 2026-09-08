package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceRerunTest {

    private static final long SOURCE_TASK_ID = 101L;
    private static final long SPACE_ID = 201L;
    private static final long AGENT_ID = 301L;
    private static final long DOCUMENT_ID = 401L;
    private static final long USER_ID = 501L;

    @Mock private TaskMapper taskMapper;
    @Mock private A2aTaskClient a2aTaskClient;
    @Mock private AgentFeign agentFeign;
    @Mock private DocumentFeign documentFeign;
    @Mock private TaskMessagePublisher messagePublisher;
    @Mock private TaskCapabilityCryptoService cryptoService;
    @Mock private AuthFeign authFeign;
    @Mock private AuditLogService auditLogService;
    @Mock private TaskCapabilityVerifier taskCapabilityVerifier;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskMapper, a2aTaskClient, agentFeign, documentFeign,
                messagePublisher, cryptoService, authFeign, auditLogService,
                new ObjectMapper(), taskCapabilityVerifier);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(USER_ID))
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsNewTaskSoAgentIdempotencyDoesNotReplayFailedExecution() {
        TaskEntity source = failedTask();
        when(taskMapper.selectById(SOURCE_TASK_ID)).thenReturn(source);
        when(documentFeign.checkSpacePermission(SPACE_ID, "task:create")).thenReturn(Result.ok());
        when(documentFeign.getExecutionContext(DOCUMENT_ID)).thenReturn(Result.ok(
                new DocumentExecutionContextVO(DOCUMENT_ID, SPACE_ID, DocType.DRAFT.getCode(), 1, 7L, 50L)));
        when(agentFeign.getExecutionProfile(AGENT_ID)).thenReturn(Result.ok(
                new AgentExecutionProfileVO(AGENT_ID, SPACE_ID, 1L, 8_000L,
                        null, 9L, true, null, null)));
        when(authFeign.issueTaskCapability(any(TaskCapabilityIssueDTO.class))).thenReturn(Result.ok("capability"));
        when(cryptoService.encrypt("capability")).thenReturn("encrypted");

        TaskVO result = service.rerun(SOURCE_TASK_ID);

        ArgumentCaptor<TaskEntity> inserted = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskMapper).insert((TaskEntity) inserted.capture());
        TaskEntity rerun = inserted.getValue();
        assertThat(rerun.getId()).isNotEqualTo(SOURCE_TASK_ID);
        assertThat(rerun.getParentTaskId()).isEqualTo(SOURCE_TASK_ID);
        assertThat(rerun.getAgentConfigVersion()).isEqualTo(9L);
        assertThat(rerun.getStatus()).isEqualTo(TaskStatus.PENDING.getCode());
        assertThat(result.id()).isEqualTo(rerun.getId());
        verify(messagePublisher).publish(rerun.getId());
    }

    private TaskEntity failedTask() {
        TaskEntity task = new TaskEntity();
        task.setId(SOURCE_TASK_ID);
        task.setTaskNo("TASK-20260908-101");
        task.setSpaceId(SPACE_ID);
        task.setAgentId(AGENT_ID);
        task.setAgentConfigVersion(3L);
        task.setDocumentId(DOCUMENT_ID);
        task.setDocumentType(DocType.DRAFT.getCode());
        task.setName("失败任务");
        task.setInstruction("继续处理文档");
        task.setStatus(TaskStatus.FAILED.getCode());
        task.setTokenBudget(4_000L);
        task.setReadScope(TaskReadScope.FULL.name());
        task.setFocusRegionsJson("[]");
        task.setRetryCount(3);
        return task;
    }
}
