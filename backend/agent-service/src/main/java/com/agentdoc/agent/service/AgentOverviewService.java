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
 * Agent概览统计服务
 * 用于空间首页统计：启用Agent、Skill、MCP服务的数量
 * 做权限隔离，无权限则对应统计字段返回null，防止通过统计接口泄露资源数量
 */
@Service
@RequiredArgsConstructor
public class AgentOverviewService {

    private final AgentMapper agentMapper;
    private final SkillMapper skillMapper;
    private final McpServerMapper mcpServerMapper;
    private final SpaceAccessService spaceAccessService;

    /**
     * 查询指定空间下启用状态的Agent、Skill、外部MCP服务统计数量
     * 权限控制：不具备对应资源读取权限时，该维度统计值返回null，不返回0，避免泄露资源数量
     *
     * @param spaceId 目标空间ID
     * @return 空间能力统计VO，各字段根据权限返回对应数量或null
     */
    public AgentOverviewStatsVO getStats(Long spaceId) {
        // 判断是否拥有Agent读取权限，有权则统计启用状态Agent数量，否则返回null
        Long activeAgentCount = hasPermission(spaceId, AGENT_READ)
                ? agentMapper.selectCount(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getSpaceId, spaceId)
                .eq(AgentEntity::getStatus, AgentStatus.ENABLED.getCode()))
                : null;
        // 判断是否拥有Skill读取权限，有权则统计激活状态Skill数量，否则返回null
        Long activeSkillCount = hasPermission(spaceId, SKILL_READ)
                ? skillMapper.selectCount(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getSpaceId, spaceId)
                .eq(SkillEntity::getStatus, SkillStatus.ACTIVE.getCode()))
                : null;
        // 判断是否拥有MCP读取权限，有权则统计启用状态MCP服务数量，否则返回null
        Long enabledMcpCount = hasPermission(spaceId, MCP_READ)
                ? mcpServerMapper.selectCount(new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getSpaceId, spaceId)
                .eq(McpServerEntity::getStatus, McpServerStatus.ENABLED.getCode()))
                : null;
        return new AgentOverviewStatsVO(activeAgentCount, activeSkillCount, enabledMcpCount);
    }

    /**
     * 校验当前用户对指定空间是否拥有指定权限
     * @param spaceId 空间ID
     * @param permission 权限标识
     * @return true拥有权限，false无权限
     */
    private boolean hasPermission(Long spaceId, String permission) {
        return spaceAccessService.hasPermission(spaceId, permission);
    }
}
