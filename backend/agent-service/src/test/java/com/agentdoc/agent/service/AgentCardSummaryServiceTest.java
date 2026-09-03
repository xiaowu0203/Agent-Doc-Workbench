package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentMcpBindingMapper;
import com.agentdoc.agent.mapper.AgentSkillMapper;
import com.agentdoc.agent.mapper.McpServerMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.entity.AgentMcpBindingEntity;
import com.agentdoc.agent.pojo.entity.AgentSkillEntity;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.vo.McpToolVO;
import com.agentdoc.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCardSummaryServiceTest {

    @Test
    void summarizesCurrentPageBindingsAndDeduplicatesTools() {
        AgentSkillMapper agentSkillMapper = mock(AgentSkillMapper.class);
        SkillVersionMapper skillVersionMapper = mock(SkillVersionMapper.class);
        AgentMcpBindingMapper agentMcpBindingMapper = mock(AgentMcpBindingMapper.class);
        McpServerMapper mcpServerMapper = mock(McpServerMapper.class);
        AgentCardSummaryService service = new AgentCardSummaryService(agentSkillMapper, skillVersionMapper,
                agentMcpBindingMapper, mcpServerMapper);

        AgentSkillEntity firstSkill = skillBinding(1L, 11L);
        AgentSkillEntity secondSkill = skillBinding(1L, 12L);
        SkillVersionEntity firstVersion = skillVersion(11L,
                List.of("document_read", "maps__search"));
        SkillVersionEntity secondVersion = skillVersion(12L,
                List.of("document_read", "document_write"));
        when(agentSkillMapper.selectList(any())).thenReturn(List.of(firstSkill, secondSkill));
        when(skillVersionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(firstVersion, secondVersion));

        AgentMcpBindingEntity mcpBinding = new AgentMcpBindingEntity();
        mcpBinding.setAgentId(1L);
        mcpBinding.setMcpServerId(21L);
        mcpBinding.setEnabled(true);
        McpServerEntity server = new McpServerEntity();
        server.setId(21L);
        server.setServerKey("maps");
        server.setDiscoveredToolsJson(JsonUtils.toJson(List.of(
                new McpToolVO("search", "搜索", "{}"),
                new McpToolVO("route", "路径规划", "{}"))));
        when(agentMcpBindingMapper.selectList(any())).thenReturn(List.of(mcpBinding));
        when(mcpServerMapper.selectBatchIds(anyCollection())).thenReturn(List.of(server));

        AgentCardSummaryService.CardSummary summary = service.summarize(List.of(1L)).get(1L);

        assertThat(summary.skillCount()).isEqualTo(2);
        assertThat(summary.mcpCount()).isEqualTo(1);
        assertThat(summary.toolCount()).isEqualTo(4);
    }

    private AgentSkillEntity skillBinding(Long agentId, Long versionId) {
        AgentSkillEntity binding = new AgentSkillEntity();
        binding.setAgentId(agentId);
        binding.setSkillVersionId(versionId);
        binding.setEnabled(true);
        return binding;
    }

    private SkillVersionEntity skillVersion(Long id, List<String> tools) {
        SkillVersionEntity version = new SkillVersionEntity();
        version.setId(id);
        version.setAllowedToolsJson(JsonUtils.toJson(tools));
        return version;
    }
}
