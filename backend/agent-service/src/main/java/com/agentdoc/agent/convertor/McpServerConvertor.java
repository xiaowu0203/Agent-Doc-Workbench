package com.agentdoc.agent.convertor;

import com.agentdoc.agent.constant.McpConstant;
import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.enums.McpConnectionStatus;
import com.agentdoc.agent.enums.McpServerStatus;
import com.agentdoc.agent.pojo.dto.McpServerCreateDTO;
import com.agentdoc.agent.pojo.dto.McpServerUpdateDTO;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.vo.McpServerVO;

/**
 * MCPServerEntity实例转换器
 * <p>
 * 负责MCP Server DTO、数据库Entity、对外VO视图之间对象转换；
 * 封装新增组装、更新字段覆写、Entity转VO逻辑；
 * 鉴权加密后的token由上层传入，本转换器不处理加密逻辑；
 * 更新时自动递增配置版本号，用于感知配置变更。
 */
public final class McpServerConvertor {

    private McpServerConvertor() {
    }

    /**
     * 创建DTO转换为数据库Entity
     *
     * @param dto               创建请求DTO
     * @param encryptedAuthToken 加密完成的鉴权令牌，由上层加密服务处理后传入
     * @return 组装完成的MCP Server实体，携带初始配置版本、启用状态
     */
    public static McpServerEntity toEntity(McpServerCreateDTO dto, String encryptedAuthToken) {
        McpServerEntity entity = new McpServerEntity();
        entity.setSpaceId(dto.spaceId());
        entity.setServerKey(dto.serverKey());
        entity.setDisplayName(dto.displayName());
        entity.setEndpointUrl(dto.endpointUrl());
        entity.setAuthType(dto.authType().name());
        entity.setAuthParamName(dto.authType() == McpAuthType.QUERY_PARAM ? dto.authParamName() : null);
        entity.setEncryptedAuthToken(encryptedAuthToken);
        // 默认版本号：1
        entity.setConfigVersion(McpConstant.INITIAL_CONFIG_VERSION);
        // 默认开启
        entity.setStatus(McpServerStatus.ENABLED.getCode());
        entity.setConnectionStatus(McpConnectionStatus.UNTESTED.name());
        entity.setDiscoveredToolCount(0);
        return entity;
    }

    /**
     * 将更新DTO字段应用到已有数据库Entity
     * <p>
     * 覆盖显示名称、端点地址、认证类型、加密令牌、状态；
     * 自动对配置版本号做增量，标记配置发生变更。
     *
     * @param entity             待更新的数据库持久化实体
     * @param dto                更新请求DTO
     * @param encryptedAuthToken 加密完成后的鉴权令牌，令牌复用/加密逻辑由上层处理完成后传入
     */
    public static void apply(McpServerEntity entity, McpServerUpdateDTO dto, String encryptedAuthToken) {
        entity.setDisplayName(dto.displayName());
        entity.setEndpointUrl(dto.endpointUrl());
        entity.setAuthType(dto.authType().name());
        entity.setAuthParamName(dto.authType() == McpAuthType.QUERY_PARAM ? dto.authParamName() : null);
        entity.setEncryptedAuthToken(encryptedAuthToken);
        entity.setStatus(dto.status());
        entity.setConfigVersion(entity.getConfigVersion() + McpConstant.CONFIG_VERSION_INCREMENT);
    }

    /**
     * 数据库Entity转换为对外VO视图对象
     * <p>
     * 安全处理：不返回明文鉴权令牌；仅输出布尔标识代表是否配置过令牌。
     *
     * @param entity MCP Server数据库实体
     * @return 对外展示VO
     */
    public static McpServerVO toVO(McpServerEntity entity) {
        return new McpServerVO(
                entity.getId(),
                entity.getSpaceId(),
                entity.getServerKey(),
                entity.getDisplayName(),
                entity.getEndpointUrl(),
                McpAuthType.valueOf(entity.getAuthType()),
                entity.getAuthParamName(),
                entity.getEncryptedAuthToken() != null,
                entity.getConfigVersion(),
                entity.getStatus(),
                McpConnectionStatus.valueOf(entity.getConnectionStatus()),
                entity.getLastTestedAt(),
                entity.getLastTestDurationMs(),
                entity.getLastTestError(),
                entity.getDiscoveredToolCount(),
                entity.getToolsDiscoveredAt());
    }
}
