package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.pojo.vo.ModelAgentCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 对模型的引用统计查询。
 */
@Service
@RequiredArgsConstructor
public class AgentModelUsageQueryService {

    private final AgentMapper agentMapper;

    /**
     * 批量统计模型作为主模型或 Skill Router 模型时关联的去重 Agent 数量。
     */
    public Map<Long, Long> countByModelIds(Collection<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Map.of();
        }
        return agentMapper.selectModelAgentCounts(modelIds).stream()
                .collect(Collectors.toMap(ModelAgentCountVO::getModelId, ModelAgentCountVO::getAgentCount,
                        (left, right) -> left));
    }
}
