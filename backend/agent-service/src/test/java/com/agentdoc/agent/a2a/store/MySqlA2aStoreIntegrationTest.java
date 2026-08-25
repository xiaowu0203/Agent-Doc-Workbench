package com.agentdoc.agent.a2a.store;

import com.agentdoc.agent.mapper.A2aPushConfigMapper;
import com.agentdoc.agent.pojo.entity.A2aPushConfigEntity;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "agent-doc.security.agent-config-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "agent-doc.security.task-capability-filter-enabled=false"
})
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TEST", matches = "true")
class MySqlA2aStoreIntegrationTest {

    private final String taskId = "it-task-" + UUID.randomUUID();
    private final String configId = "it-config-" + UUID.randomUUID();

    @Autowired
    private MySqlA2aTaskStore taskStore;

    @Autowired
    private MySqlA2aPushConfigStore pushConfigStore;

    @Autowired
    private A2aPushConfigMapper pushConfigMapper;

    @AfterEach
    void cleanUp() {
        taskStore.delete(taskId);
        pushConfigStore.deleteInfo(taskId, configId);
    }

    @Test
    void shouldPersistEncryptedTaskAndPushConfig() {
        Task task = Task.builder()
                .id(taskId)
                .contextId("it-context")
                .status(new TaskStatus(TaskState.TASK_STATE_WORKING))
                .metadata(Map.of("source", "integration-test"))
                .build();
        taskStore.save(task, true);

        Task restored = taskStore.get(taskId);
        assertThat(restored.id()).isEqualTo(task.id());
        assertThat(restored.contextId()).isEqualTo(task.contextId());
        assertThat(restored.status().state()).isEqualTo(task.status().state());
        assertThat(restored.status().timestamp().toInstant()).isEqualTo(task.status().timestamp().toInstant());
        assertThat(restored.metadata()).isEqualTo(task.metadata());
        assertThat(taskStore.isTaskActive(taskId)).isTrue();

        String notificationToken = "integration-secret-token";
        TaskPushNotificationConfig config = TaskPushNotificationConfig.builder()
                .id(configId)
                .taskId(taskId)
                .url("http://localhost/callback")
                .token(notificationToken)
                .build();
        pushConfigStore.setInfo(config, "1.0");

        assertThat(pushConfigStore.getInfo(new ListTaskPushNotificationConfigsParams(taskId)).configs())
                .containsExactly(config);
        A2aPushConfigEntity stored = pushConfigMapper.selectById(configId);
        assertThat(stored.getEncryptedPayload()).doesNotContain(notificationToken);
    }
}
