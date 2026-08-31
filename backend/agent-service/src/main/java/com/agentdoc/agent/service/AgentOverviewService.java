package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.enums.McpServerStatus;
import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.mapper.McpServerMapper;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.vo.AgentOverviewStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.MCP_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_READ;

/**
 * 空间 Agent 能力统计服务。
 */
@Service
@RequiredArgsConstructor
public class AgentOverviewService {

    private final AgentMapper agentMapper;
    private final SkillMapper skillMapper;
    private final McpServerMapper mcpServerMapper;
    private final SpaceAccessService spaceAccessService;

    /**
     * 查询空间内启用的 Agent、Skill 和外部 MCP 数量。
     * 无对应读取权限的项目返回 null，避免通过统计接口泄露资源数量。
     *
     * @param spaceId 空间 ID
     * @return 能力统计
     */
    public AgentOverviewStatsVO getStats(Long spaceId) {
        Long activeAgentCount = hasPermission(spaceId, AGENT_READ)
                ? agentMapper.selectCount(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getSpaceId, spaceId)
                .eq(AgentEntity::getStatus, AgentStatus.ENABLED.getCode()))
                : null;
        Long activeSkillCount = hasPermission(spaceId, SKILL_READ)
                ? skillMapper.selectCount(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getSpaceId, spaceId)
                .eq(SkillEntity::getStatus, SkillStatus.ACTIVE.getCode()))
                : null;
        Long enabledMcpCount = hasPermission(spaceId, MCP_READ)
                ? mcpServerMapper.selectCount(new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getSpaceId, spaceId)
                .eq(McpServerEntity::getStatus, McpServerStatus.ENABLED.getCode()))
                : null;
        return new AgentOverviewStatsVO(activeAgentCount, activeSkillCount, enabledMcpCount);
    }

    private boolean hasPermission(Long spaceId, String permission) {
        return spaceAccessService.hasPermission(spaceId, permission);
    }
}
