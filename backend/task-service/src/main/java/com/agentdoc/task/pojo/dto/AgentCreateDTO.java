package com.agentdoc.task.pojo.dto;

import com.agentdoc.task.enums.AgentStatus;
import com.agentdoc.task.pojo.entity.AgentEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Agent 创建参数。mcpConfig 只在请求进入服务时以明文存在，落库前立即加密。
 */
@Schema(description = "Agent 创建参数")
public record AgentCreateDTO(
        @Schema(description = "所属空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long spaceId,
        @Schema(description = "Agent 名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,
        @Schema(description = "Agent 描述")
        String description,
        @Schema(description = "关联 OAuth2 客户端 ID")
        String clientId,
        @Schema(description = "关联模型 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long modelId,
        @Schema(description = "MCP 连接配置，服务端会加密存储")
        String mcpConfig,
        @Schema(description = "工具白名单，逗号分隔")
        String toolWhitelist,
        @Schema(description = "可读写文档范围，JSON 格式")
        String docScope,
        @Schema(description = "Token 预算上限")
        Long tokenBudget) {

    /**
     * 转换为默认启用的 Agent 实体。
     *
     * @param encryptedMcpConfig 加密后的 MCP 配置
     * @param createdBy 创建人用户 ID
     * @return Agent 实体
     */
    public AgentEntity toEntity(String encryptedMcpConfig, Long createdBy) {
        AgentEntity entity = new AgentEntity();
        entity.setSpaceId(spaceId);
        entity.setName(name);
        entity.setDescription(description);
        entity.setClientId(clientId);
        entity.setModelId(modelId);
        entity.setMcpConfig(encryptedMcpConfig);
        entity.setToolWhitelist(toolWhitelist);
        entity.setDocScope(docScope);
        entity.setTokenBudget(tokenBudget);
        entity.setStatus(AgentStatus.ENABLED.getCode());
        entity.setCreatedBy(createdBy);
        return entity;
    }
}
