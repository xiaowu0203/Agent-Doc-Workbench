package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.common.feign.dto.EvaluationDocumentChangePreviewDTO;
import com.agentdoc.common.feign.dto.EvaluationTaskBatchQueryDTO;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangePreviewVO;
import com.agentdoc.common.security.EvaluationWorkerCapabilityVerifier;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.enums.ExecutionArtifactType;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.mapper.ExecutionArtifactMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.mcp.McpChangeProposal;
import com.agentdoc.task.pojo.entity.ExecutionArtifactEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationTaskProjectionServiceTest {

    private static final long RUN_ID = 71L;
    private static final long SPACE_ID = 9L;
    private static final long TASK_ID = 801L;

    @Mock private TaskMapper taskMapper;
    @Mock private TokenUsageDetailMapper tokenUsageDetailMapper;
    @Mock private ExecutionArtifactMapper artifactMapper;
    @Mock private ChangeRequestMapper changeRequestMapper;
    @Mock private AgentFeign agentFeign;
    @Mock private DocumentFeign documentFeign;
    @Mock private EvaluationWorkerCapabilityVerifier capabilityVerifier;
    @Mock private A2aTaskClient a2aTaskClient;
    @Mock private TaskCapabilityCryptoService cryptoService;

    private EvaluationTaskProjectionService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ExecutionArtifactEntity.class);
        service = new EvaluationTaskProjectionService(taskMapper, tokenUsageDetailMapper, artifactMapper,
                changeRequestMapper, agentFeign, documentFeign, capabilityVerifier, a2aTaskClient, cryptoService);
        when(capabilityVerifier.verify("worker-capability")).thenReturn(workerCapability());
        when(taskMapper.selectBatchIds(List.of(TASK_ID))).thenReturn(List.of(task()));
    }

    @AfterEach
    void clearCapabilityContext() {
        TaskCapabilityContext.clear();
    }

    @Test
    void previewsStructuredChangeWithReplayTaskCapabilityWithoutExposingPayload() {
        ExecutionArtifactEntity artifact = artifact(JsonUtils.toJson(new McpChangeProposal(4L,
                List.of(new ChangeItemDTO(ChangeOp.APPEND, null, "next")), "summary")));
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact));
        when(cryptoService.decrypt("encrypted-task-capability")).thenReturn("task-capability");
        when(documentFeign.previewEvaluationDocumentChanges(any())).thenAnswer(invocation -> {
            assertThat(TaskCapabilityContext.current()).isEqualTo("task-capability");
            return Result.ok(new EvaluationDocumentChangePreviewVO(301L, 4L, "b".repeat(64), false));
        });
        TaskCapabilityContext.set("outer-capability");

        var result = service.documentChanges("worker-capability", request());

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.taskId()).isEqualTo(TASK_ID);
            assertThat(value.artifactId()).isEqualTo(901L);
            assertThat(value.artifactSha256()).isEqualTo("a".repeat(64));
            assertThat(value.valid()).isTrue();
            assertThat(value.conflicted()).isFalse();
            assertThat(value.proposedContentSha256()).isEqualTo("b".repeat(64));
            assertThat(value.failureCode()).isNull();
        });
        assertThat(TaskCapabilityContext.current()).isEqualTo("outer-capability");

        ArgumentCaptor<EvaluationDocumentChangePreviewDTO> requestCaptor =
                ArgumentCaptor.forClass(EvaluationDocumentChangePreviewDTO.class);
        verify(documentFeign).previewEvaluationDocumentChanges(requestCaptor.capture());
        assertThat(requestCaptor.getValue().documentId()).isEqualTo(301L);
        assertThat(requestCaptor.getValue().baseVersion()).isEqualTo(4L);
        assertThat(requestCaptor.getValue().baseContentSha256()).isEqualTo("c".repeat(64));
        assertThat(requestCaptor.getValue().changes()).hasSize(1);
    }

    @Test
    void rejectsMalformedProposalBeforeCallingDocumentService() {
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact("not-json")));

        var result = service.documentChanges("worker-capability", request());

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.valid()).isFalse();
            assertThat(value.failureCode()).isEqualTo("ARTIFACT_SCHEMA_INVALID");
            assertThat(value.proposedContentSha256()).isNull();
        });
        verify(documentFeign, never()).previewEvaluationDocumentChanges(any());
        verify(cryptoService, never()).decrypt(any());
    }

    private static EvaluationTaskBatchQueryDTO request() {
        return new EvaluationTaskBatchQueryDTO(RUN_ID, SPACE_ID, List.of(TASK_ID));
    }

    private static TaskEntity task() {
        TaskEntity task = new TaskEntity();
        task.setId(TASK_ID);
        task.setSpaceId(SPACE_ID);
        task.setLineageType(TaskLineageType.REPLAY.name());
        task.setExecutionMode(TaskExecutionMode.ISOLATED.name());
        task.setDocumentId(301L);
        task.setDocumentVersionSnapshot(4L);
        task.setDocumentContentSha256("c".repeat(64));
        task.setCapabilityToken("encrypted-task-capability");
        return task;
    }

    private static ExecutionArtifactEntity artifact(String payload) {
        ExecutionArtifactEntity artifact = new ExecutionArtifactEntity();
        artifact.setId(901L);
        artifact.setTaskId(TASK_ID);
        artifact.setArtifactType(ExecutionArtifactType.CHANGE_PROPOSAL.name());
        artifact.setSequenceNo(1);
        artifact.setPayloadJson(payload);
        artifact.setPayloadSha256("a".repeat(64));
        return artifact;
    }

    private static Jwt workerCapability() {
        return Jwt.withTokenValue("worker-capability")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim(JwtConstant.CLAIM_RUN_ID, RUN_ID)
                .claim(JwtConstant.CLAIM_SPACE_ID, SPACE_ID)
                .claim(JwtConstant.CLAIM_TASK_IDS_HASH,
                        StableSnapshotUtils.snapshotHash(1, List.of(TASK_ID)))
                .claim(JwtConstant.CLAIM_WORKER_ACTIONS,
                        List.of(JwtConstant.ACTION_VALIDATE_DOCUMENT_CHANGE))
                .build();
    }
}
