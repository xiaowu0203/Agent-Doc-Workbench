package com.agentdoc.agent.service;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.convertor.AgentMcpBindingConvertor;
import com.agentdoc.agent.enums.McpServerStatus;
import com.agentdoc.agent.execution.context.ExternalMcpConnection;
import com.agentdoc.agent.mapper.AgentMcpBindingMapper;
import com.agentdoc.agent.pojo.dto.AgentMcpBindingItemDTO;
import com.agentdoc.agent.pojo.dto.AgentMcpBindingReplaceDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentMcpBindingEntity;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.vo.AgentMcpBindingVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_BIND_MCP;
import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_READ;

/**
 * Agent与MCP服务绑定业务服务
 * <p>
 * 负责Agent绑定MCP Server的CRUD、替换绑定关系、权限校验、配置版本号更新；
 * 对外输出绑定VO视图；捕获已启用的外部MCP连接，供给Agent运行时加载MCP服务。
 * 支持工具白名单配置：控制该Agent可调用MCP Server下哪些工具。
 */
@Service
@RequiredArgsConstructor
public class AgentMcpBindingService {
    private final AgentService agentService;
    private final SpaceAccessService spaceAccessService;
    private final AgentMcpBindingMapper bindingMapper;
    private final McpServerService mcpServerService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 查询指定Agent的MCP绑定关系列表
     *
     * @param agentId Agent主键ID
     * @return 绑定关系VO列表
     */
    public List<AgentMcpBindingVO> list(Long agentId) {
        // 校验Agent存在性
        AgentEntity agent = agentService.require(agentId);
        // 空间查看权限校验
        spaceAccessService.requirePermission(agent.getSpaceId(), AGENT_READ);
        return bindingViews(agentId);
    }

    /**
     * 全量替换Agent的MCP Server绑定关系
     * <p>
     * 逻辑：
     * 1. 悲观锁锁定Agent记录，校验Agent存在；校验当前用户拥有空间所有者权限
     * 2. 校验传入MCP Server ID不能重复、服务必须存在、归属同一空间、服务状态为已启用
     * 3. 对比新旧绑定集合：已有绑定更新启用状态与工具白名单；新增绑定插入数据库
     * 4. 更新Agent配置版本号，触发Agent运行时感知配置变更
     *
     * @param agentId Agent主键ID
     * @param dto     替换绑定请求DTO，携带待绑定MCP Server及工具白名单
     * @return 更新后完整绑定关系VO列表
     */
    public List<AgentMcpBindingVO> replace(Long agentId, AgentMcpBindingReplaceDTO dto) {
        AgentEntity permissionAgent = agentService.require(agentId);
        spaceAccessService.requirePermission(permissionAgent.getSpaceId(), AGENT_BIND_MCP);
        return transactionTemplate.execute(status -> replaceLocked(agentId, dto));
    }

    private List<AgentMcpBindingVO> replaceLocked(Long agentId, AgentMcpBindingReplaceDTO dto) {
        // 悲观锁锁定Agent行，防止并发修改绑定
        AgentEntity agent = agentService.requireForUpdate(agentId);

        List<AgentMcpBindingItemDTO> requested = dto.bindings();
        // 提取待绑定MCP Server ID集合
        List<Long> serverIds = requested.stream()
                .map(AgentMcpBindingItemDTO::mcpServerId)
                .toList();

        // 校验：不能传入重复的MCP Server ID
        if (serverIds.size() != new HashSet<>(serverIds).size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "MCP Server ID 不能重复");
        }

        // 批量查询MCP Server实体
        Map<Long, McpServerEntity> servers = new HashMap<>();
        if (!serverIds.isEmpty()) {
            mcpServerService.findByIdsForUpdate(serverIds)
                    .forEach(value ->
                            servers.put(value.getId(), value)
                    );
        }

        // 校验：传入的全部MCP Server必须真实存在
        if (servers.size() != serverIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "MCP Server 不存在");
        }

        // 业务合法性校验：空间归属、服务启用状态
        for (McpServerEntity server : servers.values()) {
            if (!agent.getSpaceId().equals(server.getSpaceId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "MCP Server 不属于 Agent 所在空间");
            }
            if (!McpServerStatus.ENABLED.matches(server.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "只能绑定已启用的 MCP Server");
            }
        }

        // 查询数据库当前已存在的绑定关系
        List<AgentMcpBindingEntity> current = bindingMapper.selectList(
                new LambdaQueryWrapper<AgentMcpBindingEntity>().eq(AgentMcpBindingEntity::getAgentId, agentId));
        Map<Long, AgentMcpBindingEntity> byServer = new HashMap<>();
        current.forEach(value ->
                byServer.put(value.getMcpServerId(), value)
        );

        // 请求参数按MCP ServerId分组
        Map<Long, AgentMcpBindingItemDTO> requestedByServer = new HashMap<>();
        requested.forEach(value -> requestedByServer.put(value.mcpServerId(), value));

        // 更新存量绑定：不在新请求集合则置为禁用；存在则更新工具白名单并启用
        for (AgentMcpBindingEntity relation : current) {
            AgentMcpBindingItemDTO item = requestedByServer.get(relation.getMcpServerId());
            AgentMcpBindingConvertor.apply(relation, item);
            bindingMapper.updateById(relation);
        }

        // 新增绑定：请求中有、数据库不存在的绑定记录执行insert
        for (AgentMcpBindingItemDTO item : requested) {
            if (!byServer.containsKey(item.mcpServerId())) {
                bindingMapper.insert(AgentMcpBindingConvertor.toEntity(agentId, item));
            }
        }

        // 递增Agent配置版本号，用于运行时识别配置发生变更
        long version = agent.getConfigVersion() == null ? AgentConstant.INITIAL_CONFIG_VERSION
                : agent.getConfigVersion();
        agent.setConfigVersion(version + AgentConstant.CONFIG_VERSION_INCREMENT);
        agentService.updateConfiguration(agent);
        return bindingViews(agentId);
    }

    /**
     * 捕获Agent运行时需要使用的外部MCP连接配置
     * <p>
     * 仅当Agent开启外部MCP总开关，并且绑定关系为启用状态时才返回连接；
     * 会做二次合法性校验（服务状态、空间归属），过滤掉无效绑定；
     * 输出给Agent执行引擎，用于建立MCP会话、加载可用工具集合。
     *
     * @param agent Agent实体对象
     * @return 外部MCP连接配置列表，按serverKey排序
     */
    public List<ExternalMcpConnection> captureEnabled(AgentEntity agent) {
        // Agent未开启外部MCP总开关，直接返回空集合
        if (!Boolean.TRUE.equals(agent.getExternalMcpEnabled()))
            return List.of();

        // 查询该Agent所有已启用的MCP绑定记录
        List<AgentMcpBindingEntity> bindings = bindingMapper.selectList(
                new LambdaQueryWrapper<AgentMcpBindingEntity>()
                        .eq(AgentMcpBindingEntity::getAgentId, agent.getId())
                        .eq(AgentMcpBindingEntity::getEnabled, true));
        if (bindings.isEmpty())
            return List.of();

        // 批量查询关联MCP Server
        Map<Long, McpServerEntity> mcpServerEntityMap = new HashMap<>();
        mcpServerService.findByIds(
                bindings.stream()
                        .map(AgentMcpBindingEntity::getMcpServerId).toList())
                .forEach(value ->
                        mcpServerEntityMap.put(value.getId(), value));

        // 组装运行时连接对象，过滤无效数据，按serverKey排序保证顺序稳定
        return bindings.stream().map(binding -> {
                    McpServerEntity server = mcpServerEntityMap.get(binding.getMcpServerId());
                    // 二次校验：服务存在、状态启用、空间归属匹配，不合法直接过滤
                    if (server == null || !McpServerStatus.ENABLED.matches(server.getStatus())
                            || !agent.getSpaceId().equals(server.getSpaceId()))
                        return null;
                    return new ExternalMcpConnection(server.getId(), server.getServerKey(), server.getDisplayName(),
                            server.getEndpointUrl(), server.getAuthType(), server.getEncryptedAuthToken(),
                            server.getConfigVersion(), AgentMcpBindingConvertor.parseWhitelist(
                                    binding.getToolWhitelistJson()));
                }).filter(Objects::nonNull)
                // 排序
                .sorted(Comparator.comparing(ExternalMcpConnection::serverKey))
                .toList();
    }

    /**
     * 组装AgentMcpBindingVO视图对象
     *
     * @param agentId     AgentID
     * @return 绑定VO列表，按serverKey排序
     */
    private List<AgentMcpBindingVO> bindingViews(Long agentId) {
        LambdaQueryWrapper<AgentMcpBindingEntity> query = new LambdaQueryWrapper<AgentMcpBindingEntity>()
                .eq(AgentMcpBindingEntity::getAgentId, agentId)
                .eq(AgentMcpBindingEntity::getEnabled, true);
        List<AgentMcpBindingEntity> bindings = bindingMapper.selectList(query);

        if (bindings.isEmpty())
            return List.of();

        // 批量填充MCP Server信息
        Map<Long, McpServerEntity> servers = new HashMap<>();
        mcpServerService.findByIds(bindings.stream()
                        .map(AgentMcpBindingEntity::getMcpServerId)
                        .toList())
                .forEach(value ->
                        servers.put(value.getId(), value)
                );
        return bindings.stream().map(binding -> {
            McpServerEntity server = servers.get(binding.getMcpServerId());
            if (server == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "Agent MCP 绑定引用的 Server 不存在");
            }
            return AgentMcpBindingConvertor.toVO(binding, server);
        }).sorted(Comparator.comparing(AgentMcpBindingVO::serverKey)).toList();
    }
}
