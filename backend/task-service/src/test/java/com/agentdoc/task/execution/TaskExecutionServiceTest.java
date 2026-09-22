package com.agentdoc.task.execution;

import com.agentdoc.common.constant.HeaderConstants;
import com.rabbitmq.client.impl.LongStringHelper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExecutionServiceTest {

    @Test
    void readsDispatchAuthorizationFromRabbitHeaderRepresentations() {
        assertThat(read("worker-token")).isEqualTo("worker-token");
        assertThat(read("worker-token".getBytes(StandardCharsets.UTF_8))).isEqualTo("worker-token");
        assertThat(read(LongStringHelper.asLongString("worker-token"))).isEqualTo("worker-token");
    }

    private String read(Object value) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY, value);
        return TaskExecutionService.dispatchAuthorization(new Message(new byte[0], properties));
    }
}
