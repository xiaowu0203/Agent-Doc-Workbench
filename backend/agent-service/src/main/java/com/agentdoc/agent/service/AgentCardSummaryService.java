package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.SkillVersionConvertor;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 批量聚合当前页 Agent 卡片所需的绑定与工具摘要。
 */
@Service
@RequiredArgsConstructor
public class AgentCardSummaryService {

    private final AgentSkillMapper agentSkillMapper;
    private final SkillVersionMapper skillVersionMapper;
    private final AgentMcpBindingMapper agentMcpBindingMapper;
    private final McpServerMapper mcpServerMapper;

    /**
     * 仅查询当前页 Agent 的关联数据，避免列表逐卡发起查询。
     * 工具数包含全部启用绑定 Skill 版本声明的工具，以及启用绑定 MCP Server 的已发现工具，
     * 按模型可见名称去重，不受 ROUTER 当次选择结果影响。
     *
     * @param agentIds 当前页 Agent ID
     * @return 按 Agent ID 索引的卡片摘要
     */
    public Map<Long, CardSummary> summarize(Collection<Long> agentIds) {
        if (agentIds == null || agentIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, MutableSummary> summaries = new HashMap<>();
        agentIds.forEach(agentId -> summaries.put(agentId, new MutableSummary()));
        summarizeSkills(agentIds, summaries);
        summarizeMcp(agentIds, summaries);

        return summaries.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().toSummary()));
    }

    private void summarizeSkills(Collection<Long> agentIds, Map<Long, MutableSummary> summaries) {
        List<AgentSkillEntity> bindings = agentSkillMapper.selectList(
                new LambdaQueryWrapper<AgentSkillEntity>()
                        .in(AgentSkillEntity::getAgentId, agentIds)
                        .eq(AgentSkillEntity::getEnabled, true));
        if (bindings.isEmpty()) {
            return;
        }

        Map<Long, SkillVersionEntity> versions = skillVersionMapper.selectBatchIds(bindings.stream()
                        .map(AgentSkillEntity::getSkillVersionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SkillVersionEntity::getId, Function.identity()));
        for (AgentSkillEntity binding : bindings) {
            MutableSummary summary = summaries.get(binding.getAgentId());
            if (summary == null) {
                continue;
            }
            summary.skillCount++;
            SkillVersionEntity version = versions.get(binding.getSkillVersionId());
            if (version != null) {
                summary.toolNames.addAll(SkillVersionConvertor.readAllowedTools(version.getAllowedToolsJson()));
            }
        }
    }

    private void summarizeMcp(Collection<Long> agentIds, Map<Long, MutableSummary> summaries) {
        List<AgentMcpBindingEntity> bindings = agentMcpBindingMapper.selectList(
                new LambdaQueryWrapper<AgentMcpBindingEntity>()
                        .in(AgentMcpBindingEntity::getAgentId, agentIds)
                        .eq(AgentMcpBindingEntity::getEnabled, true));
        if (bindings.isEmpty()) {
            return;
        }

        Map<Long, McpServerEntity> servers = mcpServerMapper.selectBatchIds(bindings.stream()
                        .map(AgentMcpBindingEntity::getMcpServerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(McpServerEntity::getId, Function.identity()));
        for (AgentMcpBindingEntity binding : bindings) {
            MutableSummary summary = summaries.get(binding.getAgentId());
            if (summary == null) {
                continue;
            }
            McpServerEntity server = servers.get(binding.getMcpServerId());
            if (server == null) {
                continue;
            }
            summary.mcpCount++;
            for (McpToolVO tool : readDiscoveredTools(server.getDiscoveredToolsJson())) {
                summary.toolNames.add(server.getServerKey() + "__" + tool.name());
            }
        }
    }

    private List<McpToolVO> readDiscoveredTools(String json) {
        List<McpToolVO> tools = JsonUtils.parse(json, new TypeReference<List<McpToolVO>>() { });
        return tools == null ? List.of() : tools;
    }

    /**
     * Agent 卡片关联摘要。
     */
    public record CardSummary(long skillCount, long mcpCount, long toolCount) {
    }

    private static final class MutableSummary {
        private long skillCount;
        private long mcpCount;
        private final Set<String> toolNames = new HashSet<>();

        private CardSummary toSummary() {
            return new CardSummary(skillCount, mcpCount, toolNames.size());
        }
    }
}
