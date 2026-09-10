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
 * Agent卡片摘要统计服务
 * 批量统计Agent关联的Skill、MCP工具数量，用于列表卡片展示
 * 避免列表循环逐个查询数据库，采用批量查询聚合统计
 */
@Service
@RequiredArgsConstructor
public class AgentCardSummaryService {

    private final AgentSkillMapper agentSkillMapper;
    private final SkillVersionMapper skillVersionMapper;
    private final AgentMcpBindingMapper agentMcpBindingMapper;
    private final McpServerMapper mcpServerMapper;

    /**
     * 批量汇总Agent卡片摘要信息
     * 仅查询当前页Agent的关联数据，防止列表每页逐条发起数据库查询
     * 工具数量统计规则：
     * 1. 包含已启用绑定的Skill版本声明工具
     * 2. 包含已启用绑定MCP服务的已发现工具
     * 3. 工具名称按模型可见名称做去重；统计不受ROUTER运行时选择结果影响
     *
     * @param agentIds 当前分页的Agent ID集合
     * @return Map key=agentId，value=Agent卡片统计摘要
     */
    public Map<Long, CardSummary> summarize(Collection<Long> agentIds) {
        // 入参为空直接返回空Map
        if (agentIds == null || agentIds.isEmpty()) {
            return Map.of();
        }

        // 初始化每个Agent的可变统计容器
        Map<Long, MutableSummary> summaries = new HashMap<>();
        agentIds.forEach(agentId -> summaries.put(agentId, new MutableSummary()));

        // 统计Skill相关数据
        summarizeSkills(agentIds, summaries);
        // 统计MCP相关数据
        summarizeMcp(agentIds, summaries);

        // 转换为不可变的最终CardSummary返回
        return summaries.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().toSummary()));
    }

    /**
     * 批量统计Agent绑定的启用状态Skill信息
     * @param agentIds Agent ID集合
     * @param summaries 统计结果容器
     */
    private void summarizeSkills(Collection<Long> agentIds, Map<Long, MutableSummary> summaries) {
        // 查询当前页Agent下，已启用的Agent‑Skill绑定关系
        List<AgentSkillEntity> bindings = agentSkillMapper.selectList(
                new LambdaQueryWrapper<AgentSkillEntity>()
                        .in(AgentSkillEntity::getAgentId, agentIds)
                        .eq(AgentSkillEntity::getEnabled, true));

        // 无绑定数据直接返回
        if (bindings.isEmpty()) {
            return;
        }

        // 批量查询对应的Skill版本实体，构建id->实体映射，避免N+1查询
        Map<Long, SkillVersionEntity> versions = skillVersionMapper.selectBatchIds(bindings.stream()
                        .map(AgentSkillEntity::getSkillVersionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SkillVersionEntity::getId, Function.identity()));

        // 遍历绑定关系，累加统计
        for (AgentSkillEntity binding : bindings) {
            MutableSummary summary = summaries.get(binding.getAgentId());
            if (summary == null) {
                continue;
            }
            // Skill绑定计数+1
            summary.skillCount++;
            SkillVersionEntity version = versions.get(binding.getSkillVersionId());
            if (version != null) {
                // 读取Skill版本允许使用的工具名称，加入去重集合
                summary.toolNames.addAll(SkillVersionConvertor.readAllowedTools(version.getAllowedToolsJson()));
            }
        }
    }

    /**
     * 批量统计Agent绑定的启用状态MCP服务信息
     * @param agentIds Agent ID集合
     * @param summaries 统计结果容器
     */
    private void summarizeMcp(Collection<Long> agentIds, Map<Long, MutableSummary> summaries) {
        // 查询当前页Agent下，已启用的Agent‑MCP绑定关系
        List<AgentMcpBindingEntity> bindings = agentMcpBindingMapper.selectList(
                new LambdaQueryWrapper<AgentMcpBindingEntity>()
                        .in(AgentMcpBindingEntity::getAgentId, agentIds)
                        .eq(AgentMcpBindingEntity::getEnabled, true));

        // 无绑定数据直接返回
        if (bindings.isEmpty()) {
            return;
        }

        // 批量查询MCP服务实体，构建id->实体映射
        Map<Long, McpServerEntity> servers = mcpServerMapper.selectBatchIds(bindings.stream()
                        .map(AgentMcpBindingEntity::getMcpServerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(McpServerEntity::getId, Function.identity()));

        // 遍历MCP绑定关系，累加统计
        for (AgentMcpBindingEntity binding : bindings) {
            MutableSummary summary = summaries.get(binding.getAgentId());
            if (summary == null) {
                continue;
            }
            McpServerEntity server = servers.get(binding.getMcpServerId());
            if (server == null) {
                continue;
            }

            // MCP服务计数+1
            summary.mcpCount++;

            // 解析MCP服务已发现工具列表，拼接服务key+工具名做唯一标识，加入去重集合
            for (McpToolVO tool : readDiscoveredTools(server.getDiscoveredToolsJson())) {
                summary.toolNames.add(server.getServerKey() + "__" + tool.name());
            }
        }
    }

    /**
     * 解析MCP服务发现的工具JSON字符串
     * @param json MCP服务discoveredToolsJson字段
     * @return MCP工具列表；解析失败返回空集合
     */
    private List<McpToolVO> readDiscoveredTools(String json) {
        List<McpToolVO> tools = JsonUtils.parse(json, new TypeReference<List<McpToolVO>>() { });
        return tools == null ? List.of() : tools;
    }

    /**
     * Agent卡片展示的统计摘要记录对象（只读）
     * @param skillCount 启用Skill绑定数量
     * @param mcpCount 启用MCP服务绑定数量
     * @param toolCount 去重后的总工具数量（Skill工具+MCP工具）
     */
    public record CardSummary(long skillCount, long mcpCount, long toolCount) {
    }

    /**
     * 内部可变统计容器，用于聚合计算，最后转为只读CardSummary
     */
    private static final class MutableSummary {
        // 启用Skill绑定数量
        private long skillCount;
        // 启用MCP服务绑定数量
        private long mcpCount;
        // 工具名称去重集合，存储Skill、MCP全部工具标识
        private final Set<String> toolNames = new HashSet<>();

        /**
         * 将可变统计对象转换为对外只读的CardSummary
         * @return 卡片摘要对象
         */
        private CardSummary toSummary() {
            return new CardSummary(skillCount, mcpCount, toolNames.size());
        }
    }
}
