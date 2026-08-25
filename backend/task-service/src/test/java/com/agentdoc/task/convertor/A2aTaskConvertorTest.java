package com.agentdoc.task.convertor;

import com.agentdoc.common.constant.A2aMetadataConstant;
import com.agentdoc.task.a2a.A2aTokenUsage;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.pojo.entity.TaskEntity;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class A2aTaskConvertorTest {

    @Test
    void shouldApplyCompletedTaskAndTokenMetadata() {
        Map<String, Object> metadata = Map.of(
                A2aMetadataConstant.INPUT_TOKENS, 120L,
                A2aMetadataConstant.CACHED_INPUT_TOKENS, 20L,
                A2aMetadataConstant.OUTPUT_TOKENS, 30L,
                A2aMetadataConstant.AGENT_EXECUTION_ID, 99L,
                A2aMetadataConstant.PROMPT_HASH, "prompt-hash");
        Artifact artifact = Artifact.builder()
                .artifactId("artifact-id")
                .name(A2aMetadataConstant.EXECUTION_SUMMARY_ARTIFACT)
                .parts(List.of(new TextPart("summary")))
                .metadata(metadata)
                .build();
        Task remoteTask = Task.builder()
                .id("a2a-task-id")
                .contextId("a2a-context-id")
                .status(new org.a2aproject.sdk.spec.TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(artifact))
                .build();
        TaskEntity entity = new TaskEntity();

        A2aTaskConvertor.apply(entity, remoteTask);

        assertThat(entity.getStatus()).isEqualTo(TaskStatus.COMPLETED.getCode());
        assertThat(entity.getResultSummary()).isEqualTo("summary");
        assertThat(entity.getTokensUsed()).isEqualTo(150L);
        assertThat(entity.getAgentExecutionId()).isEqualTo(99L);
        assertThat(entity.getPromptHash()).isEqualTo("prompt-hash");
        assertThat(entity.getEndTime()).isNotNull();
        A2aTokenUsage usage = A2aTaskConvertor.tokenUsage(remoteTask);
        assertThat(usage.inputTokens()).isEqualTo(120L);
        assertThat(usage.cachedInputTokens()).isEqualTo(20L);
        assertThat(usage.outputTokens()).isEqualTo(30L);
    }

    @Test
    void shouldMapWaitingAndRejectedStates() {
        assertThat(A2aTaskConvertor.mapStatus(TaskState.TASK_STATE_INPUT_REQUIRED))
                .isEqualTo(TaskStatus.WAITING_INPUT);
        assertThat(A2aTaskConvertor.mapStatus(TaskState.TASK_STATE_AUTH_REQUIRED))
                .isEqualTo(TaskStatus.WAITING_AUTH);
        assertThat(A2aTaskConvertor.mapStatus(TaskState.TASK_STATE_REJECTED))
                .isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void shouldKeepMissingTokenMetadataAsNull() {
        Task remoteTask = Task.builder()
                .id("a2a-task-id")
                .contextId("a2a-context-id")
                .status(new org.a2aproject.sdk.spec.TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of())
                .build();

        A2aTokenUsage usage = A2aTaskConvertor.tokenUsage(remoteTask);

        assertThat(usage.inputTokens()).isNull();
        assertThat(usage.cachedInputTokens()).isNull();
        assertThat(usage.outputTokens()).isNull();
        assertThat(usage.inputTokensEstimated()).isFalse();
        assertThat(usage.outputTokensEstimated()).isFalse();
    }
}
