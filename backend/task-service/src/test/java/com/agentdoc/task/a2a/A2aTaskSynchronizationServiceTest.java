package com.agentdoc.task.a2a;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.A2aMetadataConstant;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.service.TokenUsageService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2aTaskSynchronizationServiceTest {

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(A2aTaskSynchronizationServiceTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, TaskEntity.class);
    }

    @Test
    void shouldRecordCompletionOnlyWhenActiveStatusUpdateWins() {
        TaskMapper taskMapper = mock(TaskMapper.class);
        AgentFeign agentFeign = mock(AgentFeign.class);
        TokenUsageService tokenUsageService = mock(TokenUsageService.class);
        A2aTaskSynchronizationService service = new A2aTaskSynchronizationService(
                taskMapper, agentFeign, tokenUsageService);
        TaskEntity task = activeTask();
        Task remoteTask = completedTask();

        when(taskMapper.update(any(), any())).thenReturn(0);
        assertThat(service.synchronize(task, remoteTask)).isFalse();
        verify(tokenUsageService, never()).recordRemote(any(), any(), any());

        task.setStatus(TaskStatus.RUNNING.getCode());
        AgentExecutionProfileVO profile = new AgentExecutionProfileVO(
                task.getAgentId(), 20L, 30L, 1_000L, null, 1L, true,
                BigDecimal.ZERO, BigDecimal.ZERO);
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(agentFeign.getExecutionProfile(task.getAgentId())).thenReturn(Result.ok(profile));

        assertThat(service.synchronize(task, remoteTask)).isTrue();
        verify(tokenUsageService).recordRemote(any(), any(), any());
    }

    private TaskEntity activeTask() {
        TaskEntity task = new TaskEntity();
        task.setId(10L);
        task.setAgentId(11L);
        task.setStatus(TaskStatus.RUNNING.getCode());
        return task;
    }

    private Task completedTask() {
        Artifact artifact = Artifact.builder()
                .artifactId("summary")
                .name(A2aMetadataConstant.EXECUTION_SUMMARY_ARTIFACT)
                .parts(List.of(new TextPart("done")))
                .metadata(Map.of(
                        A2aMetadataConstant.INPUT_TOKENS, 3L,
                        A2aMetadataConstant.OUTPUT_TOKENS, 2L,
                        A2aMetadataConstant.AGENT_EXECUTION_ID, 12L,
                        A2aMetadataConstant.PROMPT_HASH, "hash"))
                .build();
        return Task.builder()
                .id("remote-task")
                .contextId("context")
                .status(new org.a2aproject.sdk.spec.TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(artifact))
                .build();
    }
}
