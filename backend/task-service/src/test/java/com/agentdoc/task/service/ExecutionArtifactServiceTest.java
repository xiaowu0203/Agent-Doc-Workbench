package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.dto.ExecutionArtifactAppendDTO;
import com.agentdoc.common.feign.vo.AgentExecutionReplayIdentityVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.enums.ExecutionArtifactType;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.mapper.ExecutionArtifactMapper;
import com.agentdoc.task.pojo.entity.ExecutionArtifactEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionArtifactServiceTest {

    private static final long TASK_ID = 11L;
    private static final long SOURCE_TASK_ID = 10L;
    private static final long EXECUTION_ID = 12L;
    private static final String PAYLOAD = "{\"changes\":[{\"op\":\"replace\"}]}";

    @Mock
    private ExecutionArtifactMapper artifactMapper;
    @Mock
    private TaskService taskService;
    @Mock
    private AgentFeign agentFeign;

    private ExecutionArtifactService service;

    @BeforeEach
    void setUp() {
        service = new ExecutionArtifactService(artifactMapper, taskService, agentFeign);
    }

    @Test
    void appendsValidatedArtifact() {
        ExecutionArtifactAppendDTO request = request(hash(PAYLOAD));
        when(taskService.require(TASK_ID)).thenReturn(task());
        when(agentFeign.getReplayIdentity(TASK_ID)).thenReturn(Result.ok(identity()));
        when(artifactMapper.selectCount(any())).thenReturn(0L);
        when(artifactMapper.insert(any(ExecutionArtifactEntity.class))).thenAnswer(invocation -> {
            ExecutionArtifactEntity entity = invocation.getArgument(0);
            entity.setId(99L);
            return 1;
        });

        var result = service.append(TASK_ID, "capability", request);

        assertThat(result.artifactId()).isEqualTo(99L);
        assertThat(result.payloadSha256()).isEqualTo(request.payloadSha256());
    }

    @Test
    void returnsExistingArtifactForSameSequenceAndHash() {
        ExecutionArtifactAppendDTO request = request(hash(PAYLOAD));
        when(taskService.require(TASK_ID)).thenReturn(task());
        when(agentFeign.getReplayIdentity(TASK_ID)).thenReturn(Result.ok(identity()));
        when(artifactMapper.selectOne(any())).thenReturn(existing(request));

        var result = service.append(TASK_ID, "capability", request);

        assertThat(result.artifactId()).isEqualTo(88L);
        verify(artifactMapper, never()).insert(any(ExecutionArtifactEntity.class));
    }

    @Test
    void rejectsSameSequenceWithDifferentPayload() {
        ExecutionArtifactAppendDTO request = request(hash(PAYLOAD));
        ExecutionArtifactEntity existing = existing(request);
        existing.setPayloadSha256("f".repeat(64));
        when(taskService.require(TASK_ID)).thenReturn(task());
        when(agentFeign.getReplayIdentity(TASK_ID)).thenReturn(Result.ok(identity()));
        when(artifactMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.append(TASK_ID, "capability", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("序号已被不同内容占用");
    }

    @Test
    void rejectsLiveTaskBeforeWriting() {
        TaskEntity task = task();
        task.setExecutionMode(TaskExecutionMode.LIVE.name());
        when(taskService.require(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> service.append(TASK_ID, "capability", request(hash(PAYLOAD))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅 Replay 隔离执行");

        verify(artifactMapper, never()).insert(any(ExecutionArtifactEntity.class));
    }

    private ExecutionArtifactAppendDTO request(String hash) {
        return new ExecutionArtifactAppendDTO(EXECUTION_ID, SOURCE_TASK_ID, 1, null,
                ExecutionArtifactType.CHANGE_PROPOSAL.name(), 1, PAYLOAD, hash);
    }

    private TaskEntity task() {
        TaskEntity task = new TaskEntity();
        task.setId(TASK_ID);
        task.setSpaceId(20L);
        task.setParentTaskId(SOURCE_TASK_ID);
        task.setLineageType(TaskLineageType.REPLAY.name());
        task.setExecutionMode(TaskExecutionMode.ISOLATED.name());
        return task;
    }

    private AgentExecutionReplayIdentityVO identity() {
        return new AgentExecutionReplayIdentityVO(1, EXECUTION_ID, 3, "a".repeat(64), true, false);
    }

    private ExecutionArtifactEntity existing(ExecutionArtifactAppendDTO request) {
        ExecutionArtifactEntity entity = new ExecutionArtifactEntity();
        entity.setId(88L);
        entity.setTaskId(TASK_ID);
        entity.setExecutionId(EXECUTION_ID);
        entity.setSourceTaskId(SOURCE_TASK_ID);
        entity.setSequenceNo(1);
        entity.setArtifactType(request.artifactType());
        entity.setSchemaVersion(request.schemaVersion());
        entity.setPayloadSha256(request.payloadSha256());
        return entity;
    }

    private String hash(String payload) {
        return StableSnapshotUtils.snapshotHash(1, JsonUtils.parse(payload, JsonNode.class));
    }
}
