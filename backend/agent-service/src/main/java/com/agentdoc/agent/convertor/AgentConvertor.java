package com.agentdoc.agent.convertor;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.pojo.dto.AgentCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentUpdateDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.vo.AgentVO;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

/**
 * Agent对象转换器
 * <p>
 * 负责Agent相关DTO、Entity、VO之间互相转换；
 * 处理创建、更新时默认值填充、配置版本号乐观锁递增、状态枚举转换。
 * </p>
 */
public final class AgentConvertor {

    private AgentConvertor() {
    }

    /**
     * 将创建DTO转换为数据库实体
     * <p>新建Agent时填充默认迭代次数、默认执行超时、初始配置版本、启用状态、创建人。</p>
     *
     * @param dto       Agent创建入参DTO
     * @param createdBy 创建人ID
     * @return 待入库AgentEntity实体
     */
    public static AgentEntity toEntity(AgentCreateDTO dto, Long createdBy) {
        AgentEntity entity = new AgentEntity();
        entity.setSpaceId(dto.spaceId());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setModelId(dto.modelId());
        entity.setTokenBudget(dto.tokenBudget());
        entity.setDocScope(dto.documentScope());
        entity.setToolWhitelist(toToolWhitelistJson(dto.toolWhitelist()));
        // 为空时使用全局默认最大迭代轮次
        entity.setMaxIterations(defaultValue(dto.maxIterations(), AgentConstant.DEFAULT_MAX_ITERATIONS));
        entity.setExecutionTimeoutSeconds(defaultValue(dto.executionTimeoutSeconds(),
                AgentConstant.DEFAULT_EXECUTION_TIMEOUT_SECONDS));
        // 新建配置初始版本号
        entity.setConfigVersion(AgentConstant.INITIAL_CONFIG_VERSION);
        // 新建Agent默认启用
        entity.setStatus(AgentStatus.ENABLED.getCode());
        entity.setCreatedBy(createdBy);
        return entity;
    }

    /**
     * 将更新DTO的字段应用到已有实体，执行实体原地修改
     * <p>更新时配置版本号自动+1，用于乐观锁；同时更新状态、各项运行参数。</p>
     *
     * @param entity 待更新数据库实体
     * @param dto    Agent更新入参DTO
     */
    public static void apply(AgentEntity entity, AgentUpdateDTO dto) {
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSystemPrompt(dto.systemPrompt());
        entity.setModelId(dto.modelId());
        entity.setTokenBudget(dto.tokenBudget());
        entity.setDocScope(dto.documentScope());
        entity.setToolWhitelist(toToolWhitelistJson(dto.toolWhitelist()));
        entity.setMaxIterations(defaultValue(dto.maxIterations(), AgentConstant.DEFAULT_MAX_ITERATIONS));
        entity.setExecutionTimeoutSeconds(defaultValue(dto.executionTimeoutSeconds(),
                AgentConstant.DEFAULT_EXECUTION_TIMEOUT_SECONDS));
        // 配置版本号递增，乐观锁，防止并发覆盖
        long currentVersion = entity.getConfigVersion() == null
                ? AgentConstant.INITIAL_CONFIG_VERSION
                : entity.getConfigVersion();
        entity.setConfigVersion(currentVersion + AgentConstant.CONFIG_VERSION_INCREMENT);
        // DTO状态码转枚举再回写code
        entity.setStatus(AgentStatus.fromCode(dto.status()).getCode());
    }

    /**
     * 数据库实体转换为对外返回VO对象
     *
     * @param entity Agent数据库实体
     * @return 前端展示VO，包含枚举状态对象
     */
    public static AgentVO toVO(AgentEntity entity) {
        return new AgentVO(entity.getId(), entity.getSpaceId(), entity.getName(), entity.getDescription(),
                entity.getSystemPrompt(), entity.getModelId(), entity.getTokenBudget(), entity.getDocScope(),
                parseToolWhitelist(entity.getToolWhitelist()),
                entity.getMaxIterations(),
                entity.getExecutionTimeoutSeconds(), entity.getConfigVersion(),
                AgentStatus.fromCode(entity.getStatus()), entity.getCreatedBy(), entity.getCreatedAt());
    }

    /**
     * int类型默认值工具：入参null返回默认值，否则返回原值
     *
     * @param value        输入可空Integer
     * @param defaultValue 兜底默认值
     * @return 非空int结果
     */
    private static int defaultValue(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * 将工具名列表序列化为MCP工具白名单JSON字符串
     * <p>自动去重并按字典序排序，保证相同集合生成的JSON字符串稳定，便于配置版本对比</p>
     * @param tools 工具名称列表，允许为null
     * @return JSON字符串；入参为null时返回null
     */
    private static String toToolWhitelistJson(List<String> tools) {
        return tools == null ? null : JsonUtils.toJson(tools.stream().distinct().sorted().toList());
    }

    /**
     * 解析MCP工具白名单JSON字符串为工具名列表
     * @param value 数据库存储的JSON字符串，可为null/空白字符串
     * @return 工具名称列表；输入null或空白返回null；解析结果为null返回空集合List.of()
     */
    private static List<String> parseToolWhitelist(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<String> tools = JsonUtils.parse(value, new TypeReference<List<String>>() { });
        return tools == null ? List.of() : tools;
    }
}
