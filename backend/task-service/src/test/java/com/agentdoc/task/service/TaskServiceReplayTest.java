package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityRenewDTO;
import com.agentdoc.common.feign.dto.ReplayBatchCreateDTO;
import com.agentdoc.common.feign.dto.ReplayBatchItemDTO;
import com.agentdoc.common.feign.vo.AgentExecutionReplayIdentityVO;
import com.agentdoc.common.feign.vo.DocumentVersionExecutionContextVO;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.config.ReplayProperties;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.pojo.dto.ReplayCreateDTO;
import com.agentdoc.task.pojo.dto.TaskFocusRegionDTO;
import com.agentdoc.task.pojo.entity.TaskEntity;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceReplayTest {

    private static final long SOURCE_TASK_ID = 101L;
    private static final long SPACE_ID = 201L;
    private static final long AGENT_ID = 301L;
    private static final long DOCUMENT_ID = 401L;

    @Mock private TaskMapper taskMapper;
    @Mock private TokenUsageDetailMapper tokenUsageDetailMapper;
    @Mock private A2aTaskClient a2aTaskClient;
    @Mock private AgentFeign agentFeign;
    @Mock private DocumentFeign documentFeign;
    @Mock private TaskMessagePublisher messagePublisher;
    @Mock private TaskCapabilityCryptoService cryptoService;
    @Mock private AuthFeign authFeign;
    @Mock private AuditLogService auditLogService;
    @Mock private TaskCapabilityVerifier taskCapabilityVerifier;

    private TaskService service;
    private ReplayProperties replayProperties;

    @BeforeEach
    void setUp() {
        replayProperties = new ReplayProperties();
        service = new TaskService(taskMapper, tokenUsageDetailMapper, a2aTaskClient, agentFeign, documentFeign,
                messagePublisher, cryptoService, authFeign, auditLogService,
                new ObjectMapper(), taskCapabilityVerifier, replayProperties);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("501")
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsIsolatedReplayWithoutIssuingCapabilityBeforeDispatch() {
        TaskEntity source = sourceTask();
        when(taskMapper.selectById(SOURCE_TASK_ID)).thenReturn(source);
        when(documentFeign.checkSpacePermission(eq(SPACE_ID), any())).thenReturn(Result.ok());
        when(documentFeign.getVersionExecutionContext(DOCUMENT_ID, 7L, source.getDocumentContentSha256()))
                .thenReturn(Result.ok(new DocumentVersionExecutionContextVO(
                        DOCUMENT_ID, 7L, source.getDocumentContentSha256(), 50L)));
        when(agentFeign.getReplayIdentity(SOURCE_TASK_ID)).thenReturn(Result.ok(
                new AgentExecutionReplayIdentityVO(1, 901L, 3, "c".repeat(64), true, false)));

        var result = service.createReplay(SOURCE_TASK_ID, new ReplayCreateDTO("evaluation-attempt:1"));

        ArgumentCaptor<TaskEntity> inserted = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskMapper).insert((TaskEntity) inserted.capture());
        TaskEntity replay = inserted.getValue();
        assertThat(replay.getParentTaskId()).isEqualTo(SOURCE_TASK_ID);
        assertThat(replay.getRootTaskId()).isEqualTo(SOURCE_TASK_ID);
        assertThat(replay.getLineageType()).isEqualTo(TaskLineageType.REPLAY.name());
        assertThat(replay.getExecutionMode()).isEqualTo(TaskExecutionMode.ISOLATED.name());
        assertThat(replay.getCapabilityToken()).isNull();
        assertThat(replay.getDerivationRequestHash()).hasSize(64);
        assertThat(result.id()).isEqualTo(replay.getId());
        verify(messagePublisher).publish(replay.getId());
        verify(authFeign, never()).issueTaskCapability(any());
    }

    @Test
    void createsReplayBatchAtomicallyAndIssuesBoundWorkerCapability() {
        TaskEntity source = sourceTask();
        AtomicReference<TaskEntity> persisted = new AtomicReference<>();
        when(taskMapper.selectById(SOURCE_TASK_ID)).thenReturn(source);
        when(taskMapper.selectOne(any())).thenAnswer(invocation -> persisted.get());
        when(taskMapper.selectList(any())).thenAnswer(invocation -> persisted.get() == null
                ? List.of() : List.of(persisted.get()));
        when(documentFeign.checkSpacePermission(eq(SPACE_ID), any())).thenReturn(Result.ok());
        when(documentFeign.getVersionExecutionContext(DOCUMENT_ID, 7L, source.getDocumentContentSha256()))
                .thenReturn(Result.ok(new DocumentVersionExecutionContextVO(
                        DOCUMENT_ID, 7L, source.getDocumentContentSha256(), 50L)));
        when(agentFeign.getReplayIdentity(SOURCE_TASK_ID)).thenReturn(Result.ok(
                new AgentExecutionReplayIdentityVO(1, 901L, 3, "c".repeat(64), true, false)));
        doAnswer(invocation -> {
            List<TaskEntity> tasks = invocation.getArgument(0);
            persisted.set(tasks.getFirst());
            return null;
        }).when(taskMapper).insertBatch(any());
        when(authFeign.issueEvaluationWorkerCapability(any())).thenReturn(Result.ok("worker-token"));

        var result = service.createReplayBatch(new ReplayBatchCreateDTO(
                701L, SPACE_ID, 600L, List.of(new ReplayBatchItemDTO(SOURCE_TASK_ID, "run:701:attempt:1"))));

        assertThat(result.workerCapability()).isEqualTo("worker-token");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceTaskId()).isEqualTo(SOURCE_TASK_ID);
            assertThat(item.replayTaskId()).isEqualTo(persisted.get().getId());
            assertThat(item.taskStatus()).isEqualTo(TaskStatus.PENDING.name());
        });
        assertThat(result.taskIdsHash()).hasSize(64);
        verify(messagePublisher).publish(persisted.get().getId(), "worker-token");
        ArgumentCaptor<EvaluationWorkerCapabilityIssueDTO> capability =
                ArgumentCaptor.forClass(EvaluationWorkerCapabilityIssueDTO.class);
        verify(authFeign).issueEvaluationWorkerCapability(capability.capture());
        assertThat(capability.getValue().taskIdsHash()).isEqualTo(result.taskIdsHash());
        assertThat(capability.getValue().runId()).isEqualTo(701L);
    }

    @Test
    void renewsWorkerCapabilityForExistingReplayWithoutCreatingOrPublishingTask() {
        TaskEntity replay = new TaskEntity();
        replay.setId(801L);
        replay.setSpaceId(SPACE_ID);
        replay.setLineageType(TaskLineageType.REPLAY.name());
        replay.setExecutionMode(TaskExecutionMode.ISOLATED.name());
        when(documentFeign.checkSpacePermission(eq(SPACE_ID), any())).thenReturn(Result.ok());
        when(taskMapper.selectBatchIds(List.of(801L))).thenReturn(List.of(replay));
        when(authFeign.issueEvaluationWorkerCapability(any())).thenReturn(Result.ok("renewed-token"));

        var result = service.renewEvaluationWorkerCapability(
                new EvaluationWorkerCapabilityRenewDTO(701L, SPACE_ID, 600L, List.of(801L)));

        assertThat(result.workerCapability()).isEqualTo("renewed-token");
        assertThat(result.taskIdsHash()).isEqualTo(StableSnapshotUtils.snapshotHash(1, List.of(801L)));
        ArgumentCaptor<EvaluationWorkerCapabilityIssueDTO> capability =
                ArgumentCaptor.forClass(EvaluationWorkerCapabilityIssueDTO.class);
        verify(authFeign).issueEvaluationWorkerCapability(capability.capture());
        assertThat(capability.getValue().actions()).containsExactly(
                JwtConstant.ACTION_BATCH_READ_TASK_STATUS,
                JwtConstant.ACTION_READ_EVALUATION_EVIDENCE,
                JwtConstant.ACTION_VALIDATE_DOCUMENT_CHANGE,
                JwtConstant.ACTION_CANCEL_RUN_TASKS);
        verify(taskMapper, never()).insertBatch(any());
        verify(messagePublisher, never()).publish(any());
    }

    @Test
    void rejectsDuplicateBatchKeysBeforeAnyTaskIsCreated() {
        ReplayBatchItemDTO item = new ReplayBatchItemDTO(SOURCE_TASK_ID, "duplicate-key");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createReplayBatch(
                        new ReplayBatchCreateDTO(701L, SPACE_ID, 600L, List.of(item, item))))
                .isInstanceOf(com.agentdoc.common.exception.BusinessException.class);

        verify(taskMapper, never()).insertBatch(any());
    }

    @Test
    void rejectsReplayCreationWhenEmergencyGateIsDisabled() {
        replayProperties.setCreationEnabled(false);

        assertThatThrownBy(() -> service.createReplay(SOURCE_TASK_ID,
                new ReplayCreateDTO("evaluation-attempt:disabled")))
                .isInstanceOfSatisfying(com.agentdoc.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode()));
        verify(taskMapper, never()).selectById(any());
    }

    private TaskEntity sourceTask() {
        TaskEntity task = new TaskEntity();
        task.setId(SOURCE_TASK_ID);
        task.setTaskNo("T-20260917-101");
        task.setSpaceId(SPACE_ID);
        task.setAgentId(AGENT_ID);
        task.setAgentConfigVersion(3L);
        task.setDocumentId(DOCUMENT_ID);
        task.setDocumentType(DocType.DRAFT.getCode());
        task.setDocumentVersionSnapshot(7L);
        task.setDocumentContentSha256("a".repeat(64));
        task.setName("来源任务");
        task.setInstruction("处理冻结文档");
        task.setStatus(TaskStatus.COMPLETED.getCode());
        task.setTokenBudget(4_000L);
        task.setReadScope(TaskReadScope.FULL.name());
        task.setFocusRegionsJson("[]");
        task.setRootTaskId(SOURCE_TASK_ID);
        task.setLineageType(TaskLineageType.ORIGINAL.name());
        task.setExecutionMode(TaskExecutionMode.LIVE.name());
        task.setInputSnapshotSchemaVersion(1);
        task.setInputSnapshotHash(StableSnapshotUtils.snapshotHash(1, new InputSnapshot(
                task.getInstruction(), SPACE_ID, DOCUMENT_ID, task.getDocumentType(), 7L,
                task.getDocumentContentSha256(), TaskReadScope.FULL.name(), List.of(), 4_000L,
                TaskLineageType.ORIGINAL.name(), TaskExecutionMode.LIVE.name())));
        return task;
    }

    private record InputSnapshot(String instruction, Long spaceId, Long documentId, Integer documentType,
                                 Long documentVersion, String documentContentSha256, String readScope,
                                 List<TaskFocusRegionDTO> focusRegions, Long tokenBudget,
                                 String lineageType, String executionMode) {
    }
}
