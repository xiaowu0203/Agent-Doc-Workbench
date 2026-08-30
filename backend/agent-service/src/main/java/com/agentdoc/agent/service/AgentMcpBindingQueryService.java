package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentMcpBindingMapper;
import com.agentdoc.agent.pojo.entity.AgentMcpBindingEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Agent MCP 绑定关系查询服务。
 */
@Service
@RequiredArgsConstructor
public class AgentMcpBindingQueryService {

    private final AgentMcpBindingMapper bindingMapper;

    /**
     * 判断 MCP Server 是否仍存在启用绑定。
     *
     * @param mcpServerId MCP Server ID
     * @return 是否存在启用绑定
     */
    public boolean hasEnabledBinding(Long mcpServerId) {
        return bindingMapper.selectCount(new LambdaQueryWrapper<AgentMcpBindingEntity>()
                .eq(AgentMcpBindingEntity::getMcpServerId, mcpServerId)
                .eq(AgentMcpBindingEntity::getEnabled, true)) > 0;
    }
}
