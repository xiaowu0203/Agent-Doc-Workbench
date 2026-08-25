package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.execution.tool.TokenUsageEstimator;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.common.pojo.TokenValue;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlibabaRuntimeUsageCollectorTest {

    @Test
    void preservesAdapterProvidedCachedInputUsage() {
        ModelAdapter adapter = mock(ModelAdapter.class);
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("done"))));
        when(adapter.tokenUsage(response)).thenReturn(new TokenUsage(
                TokenValue.provider(10L), TokenValue.provider(3L), TokenValue.provider(4L)));
        AlibabaRuntimeControl control = new AlibabaRuntimeControl(new AgentEntity(), null,
                () -> false, new TokenUsageEstimator());
        AlibabaRuntimeUsageCollector collector = new AlibabaRuntimeUsageCollector(control, adapter);

        collector.accept(response, List.of(), List.of());

        assertThat(collector.total().cachedInput()).isEqualTo(TokenValue.provider(3L));
    }
}
