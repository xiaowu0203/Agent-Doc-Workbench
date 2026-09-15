package com.agentdoc.agent.execution.application;

import com.agentdoc.agent.enums.AgentExecutionStatus;
import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.common.pojo.TokenValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentExecutionPersistenceServiceTest {

    @Test
    void persistsPartialUsageWhenExecutionFails() {
        AgentExecutionMapper mapper = mock(AgentExecutionMapper.class);
        AgentExecutionPersistenceService service = new AgentExecutionPersistenceService(mapper);
        AgentExecutionEntity execution = new AgentExecutionEntity();
        TokenUsage usage = new TokenUsage(TokenValue.provider(12L), TokenValue.estimated(3L),
                TokenValue.provider(5L));

        service.markFailed(execution, "模型第二轮失败", usage);

        assertEquals(AgentExecutionStatus.FAILED.name(), execution.getStatus());
        assertEquals(12L, execution.getInputTokens());
        assertEquals(false, execution.getInputTokensEstimated());
        assertEquals(3L, execution.getCachedInputTokens());
        assertEquals(true, execution.getCachedInputTokensEstimated());
        assertEquals(5L, execution.getOutputTokens());
        assertEquals(false, execution.getOutputTokensEstimated());
        assertEquals("模型第二轮失败", execution.getErrorMessage());
        assertNotNull(execution.getFinishedAt());
        verify(mapper).updateById(execution);
    }

    @Test
    void persistsPartialUsageWhenExecutionIsCanceled() {
        AgentExecutionMapper mapper = mock(AgentExecutionMapper.class);
        AgentExecutionPersistenceService service = new AgentExecutionPersistenceService(mapper);
        AgentExecutionEntity execution = new AgentExecutionEntity();
        TokenUsage usage = new TokenUsage(TokenValue.estimated(8L), TokenValue.unavailable(),
                TokenValue.estimated(2L));

        service.markCanceled(execution, usage);

        assertEquals(AgentExecutionStatus.CANCELED.name(), execution.getStatus());
        assertEquals(8L, execution.getInputTokens());
        assertEquals(true, execution.getInputTokensEstimated());
        assertEquals(2L, execution.getOutputTokens());
        assertEquals(true, execution.getOutputTokensEstimated());
        assertNotNull(execution.getFinishedAt());
        verify(mapper).updateById(execution);
    }
}
