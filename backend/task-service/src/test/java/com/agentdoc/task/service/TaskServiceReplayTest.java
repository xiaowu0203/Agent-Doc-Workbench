package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityRenewDTO;
import com.agentdoc.common.feign.dto.ExperimentBatchCreateDTO;
import com.agentdoc.common.feign.dto.ExperimentBatchItemDTO;
import com.agentdoc.common.feign.dto.ReplayBatchCreateDTO;
import com.agentdoc.common.feign.dto.ReplayBatchItemDTO;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.feign.context.AuthorizationContext;
import com.agentdoc.common.feign.interceptor.AuthHeaderForwardInterceptor;
import com.agentdoc.common.feign.vo.AgentExecutionReplayIdentityVO;
import com.agentdoc.common.feign.vo.AgentCandidateConfigVO;
import com.agentdoc.common.feign.vo.DocumentVersionExecutionContextVO;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.config.ReplayProperties;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.execution.TaskExecutionService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.web.context.request.RequestContextHolder;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import com.rabbitmq.client.Channel;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import feign.RequestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

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
        AuthorizationContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void createsManualReplayWithEncryptedIsolatedCapabilityBeforePublishing() {
        prepareManualReplaySource();
        prepareManualReplayCapability();

        var result = service.createReplay(SOURCE_TASK_ID, new ReplayCreateDTO("manual-replay:1"));

        ArgumentCaptor<TaskCapabilityIssueDTO> capability = ArgumentCaptor.forClass(TaskCapabilityIssueDTO.class);
        ArgumentCaptor<TaskEntity> inserted = ArgumentCaptor.forClass(TaskEntity.class);
        var order = inOrder(authFeign, cryptoService, taskMapper, messagePublisher);
        order.verify(authFeign).issueTaskCapability(capability.capture());
        order.verify(cryptoService).encrypt("replay-capability");
        order.verify(taskMapper).insert(inserted.capture());
        TaskEntity replay = inserted.getValue();
        order.verify(messagePublisher).publish(replay.getId());
        assertThat(replay.getParentTaskId()).isEqualTo(SOURCE_TASK_ID);
        assertThat(replay.getRootTaskId()).isEqualTo(SOURCE_TASK_ID);
        assertThat(replay.getLineageType()).isEqualTo(TaskLineageType.REPLAY.name());
        assertThat(replay.getExecutionMode()).isEqualTo(TaskExecutionMode.ISOLATED.name());
        assertThat(replay.getCapabilityToken()).isEqualTo("encrypted-replay-capability");
        assertThat(replay.getDerivationRequestHash()).hasSize(64);
        assertThat(result.id()).isEqualTo(replay.getId());
        assertThat(capability.getValue().taskId()).isEqualTo(replay.getId());
        assertThat(capability.getValue().agentId()).isEqualTo(AGENT_ID);
        assertThat(capability.getValue().spaceId()).isEqualTo(SPACE_ID);
        assertThat(capability.getValue().documentId()).isEqualTo(DOCUMENT_ID);
        assertThat(capability.getValue().executionMode()).isEqualTo(TaskExecutionMode.ISOLATED.name());
        assertThat(capability.getValue().documentVersionSnapshot()).isEqualTo(replay.getDocumentVersionSnapshot());
        assertThat(capability.getValue().documentContentSha256()).isEqualTo(replay.getDocumentContentSha256());
        assertThat(capability.getValue().inputSnapshotSchemaVersion()).isEqualTo(replay.getInputSnapshotSchemaVersion());
        assertThat(capability.getValue().inputSnapshotHash()).isEqualTo(replay.getInputSnapshotHash());
        assertThat(capability.getValue().derivationRequestHash()).isEqualTo(replay.getDerivationRequestHash());
        assertThat(capability.getValue().actions()).containsExactly(
                JwtConstant.ACTION_READ_FRAGMENT, JwtConstant.ACTION_CAPTURE_EXECUTION_ARTIFACT);
        assertThat(JsonUtils.toJson(result)).doesNotContain("replay-capability");
        verify(authFeign, never()).issueEvaluationWorkerCapability(any());
        verify(messagePublisher, never()).publish(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    void dispatchesManualReplayWithScopedAuthorizationAndAlwaysClearsContext(
            boolean withWorker, boolean sourceUnavailable) throws Exception {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(TaskMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, TaskEntity.class);
        prepareManualReplaySource();
        prepareManualReplayCapability();
        service.createReplay(SOURCE_TASK_ID, new ReplayCreateDTO("manual-replay:mq"));
        ArgumentCaptor<TaskEntity> inserted = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskMapper).insert(inserted.capture());
        TaskEntity replay = inserted.getValue();
        when(taskMapper.selectById(replay.getId())).thenReturn(replay);
        when(cryptoService.decrypt("encrypted-replay-capability")).thenReturn("replay-capability");
        when(taskMapper.update(eq(null), any())).thenReturn(1);
        RedisUtils redis = mock(RedisUtils.class);
        Channel channel = mock(Channel.class);
        when(redis.setIfAbsent(any(), any(), any())).thenReturn(true);
        when(redis.deleteIfValueMatches(any(), any())).thenReturn(true);
        if (!sourceUnavailable) {
            when(a2aTaskClient.send(eq(replay), eq("replay-capability"), any())).thenReturn(
                    Task.builder().id("test-a2a").contextId("test-context")
                            .status(new org.a2aproject.sdk.spec.TaskStatus(TaskState.TASK_STATE_SUBMITTED)).build());
        }
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        AuthorizationContext.clear();
        // 模拟 Gateway：清空用户上下文后，来源复核也必须有新的 Task Bearer 身份。
        when(agentFeign.getReplayIdentity(SOURCE_TASK_ID)).thenAnswer(invocation -> {
            RequestTemplate request = new RequestTemplate();
            new AuthHeaderForwardInterceptor().apply(request);
            assertThat(request.headers().get("Authorization"))
                    .containsExactly(withWorker ? "Bearer worker-token" : "Bearer replay-capability");
            return sourceUnavailable ? Result.fail(ErrorCode.SERVICE_UNAVAILABLE)
                    : Result.ok(new AgentExecutionReplayIdentityVO(1, 901L, 3, "c".repeat(64), true, false));
        });
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(1L);
        if (withWorker) {
            properties.setHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY, "worker-token");
        } else {
            assertThat(properties.getHeaders()).isEmpty();
        }
        TaskExecutionService consumer = new TaskExecutionService(service, taskMapper, a2aTaskClient,
                messagePublisher, redis, auditLogService);

        consumer.consume(replay.getId(), new Message(new byte[0], properties), channel);

        verify(authFeign, times(1)).issueTaskCapability(any());
        verify(taskCapabilityVerifier).verify("replay-capability");
        verify(agentFeign, times(2)).getReplayIdentity(SOURCE_TASK_ID);
        if (sourceUnavailable) {
            verify(a2aTaskClient, never()).send(any(), any(), any());
            assertThat(replay.getA2aTaskId()).isNull();
        } else {
            verify(a2aTaskClient).send(eq(replay), eq("replay-capability"), any());
            assertThat(replay.getA2aTaskId()).isEqualTo("test-a2a");
        }
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        assertThat(AuthorizationContext.current()).isNull();
    }

    @Test
    void rejectedSigningDoesNotCreateOrPublishManualReplay() {
        prepareManualReplaySource();
        when(authFeign.issueTaskCapability(any())).thenReturn(Result.fail(ErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> service.createReplay(SOURCE_TASK_ID, new ReplayCreateDTO("manual-replay:rejected")))
                .isInstanceOf(BusinessException.class);

        verify(taskMapper, never()).insert(any(TaskEntity.class));
        verify(messagePublisher, never()).publish(any());
        verify(messagePublisher, never()).publish(any(), any());
        verify(cryptoService, never()).encrypt(any());
    }

    @Test
    void rejectedEncryptionDoesNotCreateOrPublishManualReplay() {
        prepareManualReplaySource();
        prepareManualReplayCapability();
        when(cryptoService.encrypt("replay-capability")).thenThrow(new IllegalStateException("test-encryption-failure"));

        assertThatThrownBy(() -> service.createReplay(SOURCE_TASK_ID, new ReplayCreateDTO("manual-replay:crypto")))
                .isInstanceOf(IllegalStateException.class);

        verify(taskMapper, never()).insert(any(TaskEntity.class));
        verify(messagePublisher, never()).publish(any());
        verify(messagePublisher, never()).publish(any(), any());
    }

    @Test
    void deniedSpacePermissionDoesNotSignManualReplayCapability() {
        when(taskMapper.selectById(SOURCE_TASK_ID)).thenReturn(sourceTask());
        when(documentFeign.checkSpacePermission(eq(SPACE_ID), any())).thenReturn(Result.fail(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> service.createReplay(SOURCE_TASK_ID, new ReplayCreateDTO("manual-replay:denied")))
                .isInstanceOf(BusinessException.class);

        verify(authFeign, never()).issueTaskCapability(any());
        verify(taskMapper, never()).insert(any(TaskEntity.class));
        verify(messagePublisher, never()).publish(any());
    }

    @Test
    void storedCapabilityDoesNotSkipSourceRevalidationBeforeDispatch() {
        prepareManualReplaySource();
        prepareManualReplayCapability();
        service.createReplay(SOURCE_TASK_ID, new ReplayCreateDTO("manual-replay:source-invalidated"));
        ArgumentCaptor<TaskEntity> inserted = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskMapper).insert(inserted.capture());
        when(agentFeign.getReplayIdentity(SOURCE_TASK_ID)).thenReturn(Result.ok(
                new AgentExecutionReplayIdentityVO(1, 901L, 3, "c".repeat(64), false, false)));
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.requireReplayDispatchIdentity(inserted.getValue()))
                .isInstanceOf(BusinessException.class);

        verify(authFeign, times(1)).issueTaskCapability(any());
        verify(a2aTaskClient, never()).send(any(), any(), any());
    }

    @Test
    void repeatedManualReplayReturnsSameTaskWithoutReissuingOrRepublishing() {
        prepareManualReplaySource();
        prepareManualReplayCapability();
        ReplayCreateDTO request = new ReplayCreateDTO("manual-replay:idempotent");
        var first = service.createReplay(SOURCE_TASK_ID, request);
        ArgumentCaptor<TaskEntity> inserted = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskMapper).insert(inserted.capture());
        TaskEntity replay = inserted.getValue();
        replay.setStatus(TaskStatus.FAILED.getCode());
        when(taskMapper.selectOne(any())).thenReturn(replay);

        var repeated = service.createReplay(SOURCE_TASK_ID, request);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repeated.status()).isEqualTo(TaskStatus.FAILED);
        verify(authFeign, times(1)).issueTaskCapability(any());
        verify(taskMapper, times(1)).insert(any(TaskEntity.class));
        verify(messagePublisher, times(1)).publish(first.id());
    }

    @Test
    void expiredStoredReplayCapabilityIsNotRenewedBeforeDispatch() {
        TaskEntity replay = new TaskEntity();
        replay.setLineageType(TaskLineageType.REPLAY.name());
        replay.setExecutionMode(TaskExecutionMode.ISOLATED.name());
        replay.setCapabilityToken("encrypted-replay-capability");
        when(cryptoService.decrypt("encrypted-replay-capability")).thenReturn("replay-capability");
        when(taskCapabilityVerifier.verify("replay-capability")).thenThrow(new JwtException("test-expired"));

        assertThatThrownBy(() -> service.resolveDispatchCapability(replay)).isInstanceOf(JwtException.class);

        verify(authFeign, never()).issueTaskCapability(any());
        verify(taskMapper, never()).update(eq(null), any());
    }

    private void prepareManualReplayCapability() {
        when(authFeign.issueTaskCapability(any())).thenAnswer(invocation ->
                SecurityContextHolder.getContext().getAuthentication() == null
                        ? Result.fail(ErrorCode.UNAUTHORIZED) : Result.ok("replay-capability"));
        when(cryptoService.encrypt("replay-capability")).thenReturn("encrypted-replay-capability");
    }

    private void prepareManualReplaySource() {
        TaskEntity source = sourceTask();
        when(taskMapper.selectById(SOURCE_TASK_ID)).thenReturn(source);
        when(documentFeign.checkSpacePermission(eq(SPACE_ID), any())).thenReturn(Result.ok());
        when(documentFeign.getVersionExecutionContext(DOCUMENT_ID, 7L, source.getDocumentContentSha256()))
                .thenReturn(Result.ok(new DocumentVersionExecutionContextVO(
                        DOCUMENT_ID, 7L, source.getDocumentContentSha256(), 50L)));
        when(agentFeign.getReplayIdentity(SOURCE_TASK_ID)).thenReturn(Result.ok(
                new AgentExecutionReplayIdentityVO(1, 901L, 3, "c".repeat(64), true, false)));
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
    void createsExperimentBatchIdempotentlyWithFrozenCandidateIdentity() {
        TaskEntity source = sourceTask();
        AtomicReference<TaskEntity> persisted = new AtomicReference<>();
        String sourceHash = "c".repeat(64);
        String candidateHash = "d".repeat(64);
        when(taskMapper.selectById(SOURCE_TASK_ID)).thenReturn(source);
        when(taskMapper.selectOne(any())).thenAnswer(invocation -> persisted.get());
        when(taskMapper.selectList(any())).thenAnswer(invocation -> persisted.get() == null
                ? List.of() : List.of(persisted.get()));
        when(documentFeign.checkSpacePermission(eq(SPACE_ID), any())).thenReturn(Result.ok());
        when(documentFeign.getVersionExecutionContext(DOCUMENT_ID, 7L, source.getDocumentContentSha256()))
                .thenReturn(Result.ok(new DocumentVersionExecutionContextVO(
                        DOCUMENT_ID, 7L, source.getDocumentContentSha256(), 50L)));
        when(agentFeign.getReplayIdentity(SOURCE_TASK_ID)).thenReturn(Result.ok(
                new AgentExecutionReplayIdentityVO(1, 901L, 3, sourceHash, true, false)));
        when(agentFeign.getCandidateConfigIdentity(601L, SPACE_ID, candidateHash)).thenReturn(Result.ok(
                new AgentCandidateConfigVO(601L, SPACE_ID, AGENT_ID, SOURCE_TASK_ID, 901L,
                        3, sourceHash, 3, candidateHash, "e".repeat(64),
                        List.of("snapshot.systemPrompt"))));
        doAnswer(invocation -> {
            List<TaskEntity> tasks = invocation.getArgument(0);
            persisted.set(tasks.getFirst());
            return null;
        }).when(taskMapper).insertBatch(any());
        when(authFeign.issueEvaluationWorkerCapability(any())).thenReturn(Result.ok("worker-token"));
        String key = "experiment:501:variant:601:case:701:attempt:1";
        ExperimentBatchCreateDTO request = new ExperimentBatchCreateDTO(801L, 601L, SPACE_ID,
                601L, 3, candidateHash, 600L,
                List.of(new ExperimentBatchItemDTO(SOURCE_TASK_ID, 701L, 1, key)));

        var first = service.createExperimentBatch(request);
        var retried = service.createExperimentBatch(request);

        assertThat(first.items().getFirst().executionTaskId()).isEqualTo(persisted.get().getId());
        assertThat(retried.items().getFirst().executionTaskId()).isEqualTo(persisted.get().getId());
        assertThat(persisted.get().getLineageType()).isEqualTo(TaskLineageType.EXPERIMENT.name());
        assertThat(persisted.get().getExecutionMode()).isEqualTo(TaskExecutionMode.ISOLATED.name());
        assertThat(persisted.get().getCandidateConfigId()).isEqualTo(601L);
        assertThat(persisted.get().getCandidateSnapshotHash()).isEqualTo(candidateHash);
        verify(taskMapper, times(1)).insertBatch(any());
        verify(messagePublisher, times(1)).publish(persisted.get().getId(), "worker-token");
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
