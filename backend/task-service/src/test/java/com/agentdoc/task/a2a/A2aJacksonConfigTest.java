package com.agentdoc.task.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class A2aJacksonConfigTest {

    @Test
    void shouldDeserializeTextAndDataParts() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new A2aJacksonConfig().taskA2aPartJacksonCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();
        Message message = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("message-1")
                .parts(new TextPart("模型调用失败"), new DataPart("ReadTimeoutException"))
                .build();
        Task task = new Task("a2a-task-1", "context-1",
                new TaskStatus(TaskState.TASK_STATE_FAILED, message, OffsetDateTime.now()),
                null, List.of(message), null);

        Task decoded = mapper.readValue(mapper.writeValueAsString(task), Task.class);

        assertThat(decoded.status().message().parts()).hasSize(2);
        assertThat(decoded.status().message().parts().get(0)).isInstanceOf(TextPart.class);
        assertThat(decoded.status().message().parts().get(1)).isInstanceOf(DataPart.class);
    }
}
