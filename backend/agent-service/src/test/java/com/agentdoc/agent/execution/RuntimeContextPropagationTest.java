package com.agentdoc.agent.execution;

import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.ModelAdapterRegistry;
import com.agentdoc.agent.execution.runtime.AgentRuntimeContext;
import com.agentdoc.agent.execution.runtime.AgentRuntimeResult;
import com.agentdoc.agent.execution.runtime.ExecutionToolSession;
import com.agentdoc.agent.execution.runtime.ExecutionToolSessionFactory;
import com.agentdoc.agent.execution.runtime.SpringAiAgentExecutionRuntime;
import com.agentdoc.agent.execution.tool.ProviderNeutralToolLoop;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.common.feign.dto.AgentTaskInputDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeContextPropagationTest {

    @Test
    void customRuntimePassesSnapshotPromptToToolLoop() {
        AgentConfigCryptoService cryptoService = mock(AgentConfigCryptoService.class);
        ModelAdapterRegistry adapterRegistry = mock(ModelAdapterRegistry.class);
        ProviderNeutralToolLoop toolLoop = mock(ProviderNeutralToolLoop.class);
        ObjectProvider<ExecutionToolSessionFactory> provider = mock(ObjectProvider.class);
        ExecutionToolSessionFactory factory = mock(ExecutionToolSessionFactory.class);
        ExecutionToolSession session = new ExecutionToolSession(null, List.of());
        ModelAdapter adapter = mock(ModelAdapter.class);
        AgentRuntimeResult expected = mock(AgentRuntimeResult.class);
        AgentEntity agent = new AgentEntity();
        agent.setTokenBudget(100L);
        agent.setMaxIterations(2);
        ModelEntity model = new ModelEntity();
        AgentTaskInputDTO input = new AgentTaskInputDTO(1L, 2L, 3L, null, null, null, null);
        AgentRuntimeContext context = new AgentRuntimeContext(agent, model, input,
                "instruction", "fixed snapshot prompt", null, List.of());

        when(provider.getIfAvailable()).thenReturn(factory);
        when(factory.open(eq(context), any())).thenReturn(session);
        when(adapterRegistry.require(eq(model), any())).thenReturn(adapter);
        when(toolLoop.execute(eq(adapter), any(), eq("fixed snapshot prompt"), eq("instruction"),
                eq(100L), eq(2), any())).thenReturn(expected);

        SpringAiAgentExecutionRuntime runtime = new SpringAiAgentExecutionRuntime(
                cryptoService, adapterRegistry, toolLoop, provider);

        AgentRuntimeResult actual = runtime.execute(context, () -> false);

        org.assertj.core.api.Assertions.assertThat(actual).isSameAs(expected);
        verify(toolLoop).execute(eq(adapter), any(), eq("fixed snapshot prompt"), eq("instruction"),
                eq(100L), eq(2), any());
    }
}
