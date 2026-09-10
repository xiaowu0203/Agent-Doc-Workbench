package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.enums.SkillSelectionMode;
import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.pojo.param.AgentSearchParam;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceSearchTest {

    @Test
    void returnsPagedCardsWithModelAndBindingSummary() {
        AgentMapper agentMapper = mock(AgentMapper.class);
        ModelService modelService = mock(ModelService.class);
        SpaceAccessService spaceAccessService = mock(SpaceAccessService.class);
        AgentCardSummaryService summaryService = mock(AgentCardSummaryService.class);
        AgentService service = new AgentService(agentMapper, modelService, spaceAccessService, summaryService);

        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 3, 10, 30);
        AgentEntity agent = new AgentEntity();
        agent.setId(10L);
        agent.setSpaceId(20L);
        agent.setName("文档审计 Agent");
        agent.setDescription("检查文档风险");
        agent.setModelId(30L);
        agent.setSkillSelectionMode(SkillSelectionMode.ROUTER.name());
        agent.setExternalMcpEnabled(true);
        agent.setTokenBudget(50000L);
        agent.setMaxIterations(12);
        agent.setExecutionTimeoutSeconds(300);
        agent.setConfigVersion(4L);
        agent.setStatus(AgentStatus.ENABLED.getCode());
        agent.setCreatedAt(updatedAt.minusDays(1));
        agent.setUpdatedAt(updatedAt);
        ModelEntity model = new ModelEntity();
        model.setId(30L);
        model.setDisplayName("GPT-5.2");

        Page<AgentEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(agent));
        page.setTotal(1);
        when(agentMapper.selectPage(any(), any())).thenReturn(page);
        when(modelService.findByIds(any())).thenReturn(List.of(model));
        when(summaryService.summarize(List.of(10L))).thenReturn(Map.of(10L,
                new AgentCardSummaryService.CardSummary(3, 2, 9)));

        AgentSearchParam param = new AgentSearchParam();
        param.setSpaceId(20L);
        param.setStatus(AgentStatus.ENABLED.getCode());
        param.setModelId(30L);
        param.setKeyword("审计");

        var result = service.search(param);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(card -> {
            assertThat(card.modelDisplayName()).isEqualTo("GPT-5.2");
            assertThat(card.skillCount()).isEqualTo(3);
            assertThat(card.mcpCount()).isEqualTo(2);
            assertThat(card.toolCount()).isEqualTo(9);
            assertThat(card.updatedAt()).isEqualTo(updatedAt);
        });
        verify(spaceAccessService).requirePermission(20L, AGENT_READ);
        verify(summaryService).summarize(List.of(10L));
    }
}
