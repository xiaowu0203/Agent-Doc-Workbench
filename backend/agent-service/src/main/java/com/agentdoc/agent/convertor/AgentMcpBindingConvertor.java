package com.agentdoc.agent.convertor;

import com.agentdoc.agent.pojo.dto.AgentMcpBindingItemDTO;
import com.agentdoc.agent.pojo.entity.AgentMcpBindingEntity;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.vo.AgentMcpBindingVO;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

/**
 * Agent‑MCP绑定对象转换器
 * <p>
 * 负责DTO、Entity、VO之间转换；同时处理工具白名单List与JSON字符串的序列化/反序列化；
 * 白名单序列化时自动去重、排序；反序列化返回不可变List防止外部篡改内部数据。
 */
public final class AgentMcpBindingConvertor {

    private AgentMcpBindingConvertor() {
    }

    /**
     * DTO转为数据库Entity
     *
     * @param agentId Agent主键ID
     * @param dto     绑定项DTO
     * @return 组装完成的数据库实体，默认enabled=true启用状态
     */
    public static AgentMcpBindingEntity toEntity(Long agentId, AgentMcpBindingItemDTO dto) {
        AgentMcpBindingEntity entity = new AgentMcpBindingEntity();
        entity.setAgentId(agentId);
        entity.setMcpServerId(dto.mcpServerId());
        entity.setToolWhitelistJson(toWhitelistJson(dto.toolWhitelist()));
        entity.setEnabled(true);
        return entity;
    }

    /**
     * 将DTO变更应用到已有Entity
     * <p>
     * dto为null代表取消绑定：设置enabled=false；
     * dto非null：更新工具白名单配置。
     *
     * @param entity 待更新的数据库实体
     * @param dto    绑定项DTO，传null表示解除绑定
     */
    public static void apply(AgentMcpBindingEntity entity, AgentMcpBindingItemDTO dto) {
        entity.setEnabled(dto != null);
        if (dto != null) {
            entity.setToolWhitelistJson(toWhitelistJson(dto.toolWhitelist()));
        }
    }

    /**
     * Entity + McpServerEntity 组装为对外VO视图
     *
     * @param entity 绑定关系实体
     * @param mcpServerEntity 关联的MCP服务实例实体，用于填充serverKey、展示名称
     * @return 对外VO对象，包含解析完成的工具白名单列表
     */
    public static AgentMcpBindingVO toVO(AgentMcpBindingEntity entity, McpServerEntity mcpServerEntity) {
        return new AgentMcpBindingVO(
                entity.getId(),
                entity.getAgentId(),
                entity.getMcpServerId(),
                mcpServerEntity.getServerKey(),
                mcpServerEntity.getDisplayName(),
                parseWhitelist(entity.getToolWhitelistJson()),
                entity.getEnabled());
    }

    /**
     * 解析数据库JSON字符串为工具白名单列表
     * <p>
     * 返回不可变List，防止上层业务修改内部集合；JSON格式异常抛出运行时异常。
     *
     * @param json 数据库存储的白名单JSON字符串，可为null/空白
     * @return 不可变工具名列表；入参空时返回null
     * @throws IllegalStateException JSON解析失败或解析结果为null
     */
    public static List<String> parseWhitelist(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        List<String> tools = JsonUtils.parse(json, new TypeReference<List<String>>() { });
        if (tools == null) {
            throw new IllegalStateException("MCP 工具白名单 JSON 无效");
        }
        // 返回不可变集合，避免外部修改影响内部数据
        return List.copyOf(tools);
    }

    /**
     * 将内存工具白名单列表序列化为数据库JSON字符串
     * <p>
     * 自动对工具名去重、字典序排序，保证同一组工具输出JSON稳定，便于比对。
     *
     * @param tools 原始工具名称列表，可为null
     * @return 序列化后的JSON字符串；入参null返回null
     */
    private static String toWhitelistJson(List<String> tools) {
        return tools == null ? null : JsonUtils.toJson(
                tools.stream()
                        .distinct()
                        .sorted()
                        .toList()
        );
    }
}
