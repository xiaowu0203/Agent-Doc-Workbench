package com.agentdoc.agent.execution.audit;

import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.mapper.AgentExecutionModelCallMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionModelCallEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionModelCallAuditServiceTest {

    @Test
    void storesActualParametersAndHashesWithoutPersistingMessagePayload() {
        AgentExecutionModelCallMapper mapper = mock(AgentExecutionModelCallMapper.class);
        when(mapper.insert(any())).thenReturn(1);
        when(mapper.updateById(any())).thenReturn(1);
        AgentExecutionModelCallAuditService service = new AgentExecutionModelCallAuditService(mapper);
        ModelEntity model = new ModelEntity();
        model.setId(7L);
        model.setConfigVersion(3L);
        model.setModelKey("demo-model");
        ModelAdapterContext context = new ModelAdapterContext(null, model, "secret-key",
                256, 0.2, List.of()).withExecutionId(9L);

        AgentExecutionModelCallEntity audit = service.start(context, 1,
                List.of(new SystemMessage("system secret"), new UserMessage("user secret")), false);

        assertThat(audit.getExecutionId()).isEqualTo(9L);
        assertThat(audit.getModelConfigVersion()).isEqualTo(3L);
        assertThat(audit.getMaxOutputTokens()).isEqualTo(256);
        assertThat(audit.getTemperature()).isEqualTo(0.2);
        assertThat(audit.getMessagesSha256()).hasSize(64)
                .doesNotContain("system secret", "user secret", "secret-key");
        assertThat(audit.getMessagesSize()).isPositive();
        verify(mapper).insert(audit);

        service.succeed(audit, new ChatResponse(List.of(
                new Generation(new AssistantMessage("response secret")))));

        assertThat(audit.getResponseSha256()).hasSize(64).doesNotContain("response secret");
        assertThat(audit.getResponseSize()).isPositive();
        verify(mapper).updateById(any(AgentExecutionModelCallEntity.class));
    }

    @Test
    void failsClosedWhenStartAuditWasNotInserted() {
        AgentExecutionModelCallMapper mapper = mock(AgentExecutionModelCallMapper.class);
        AgentExecutionModelCallAuditService service = new AgentExecutionModelCallAuditService(mapper);
        ModelEntity model = new ModelEntity();
        model.setId(7L);
        model.setConfigVersion(3L);
        model.setModelKey("demo-model");
        ModelAdapterContext context = new ModelAdapterContext(null, model, "secret-key",
                256, 0.2, List.of()).withExecutionId(9L);

        assertThatThrownBy(() -> service.start(context, 1, List.of(new UserMessage("task")), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审计记录创建失败");
    }
}
