package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.pojo.vo.ModelAgentCountVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentModelUsageQueryServiceTest {

    @Test
    void returnsCountsUsingOneBatchQuery() {
        AgentMapper mapper = mock(AgentMapper.class);
        ModelAgentCountVO count = new ModelAgentCountVO();
        count.setModelId(7L);
        count.setAgentCount(3L);
        when(mapper.selectModelAgentCounts(List.of(7L, 8L))).thenReturn(List.of(count));
        AgentModelUsageQueryService service = new AgentModelUsageQueryService(mapper);

        var result = service.countByModelIds(List.of(7L, 8L));

        assertThat(result).containsEntry(7L, 3L).doesNotContainKey(8L);
        verify(mapper).selectModelAgentCounts(List.of(7L, 8L));
    }
}
