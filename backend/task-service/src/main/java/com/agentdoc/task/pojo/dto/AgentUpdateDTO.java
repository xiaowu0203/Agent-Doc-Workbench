package com.agentdoc.task.pojo.dto;

import com.agentdoc.task.pojo.entity.AgentEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Agent 更新参数。mcpConfig 为空时保留原密文。
 */
@Schema(description = "Agent 更新参数")
public record AgentUpdateDTO(
        @Schema(description = "Agent 名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,
        @Schema(description = "Agent 描述")
        String description,
        @Schema(description = "关联 OAuth2 客户端 ID")
        String clientId,
        @Schema(description = "关联模型 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long modelId,
        @Schema(description = "MCP 连接配置，为空时保留原配置")
        String mcpConfig,
        @Schema(description = "工具白名单，逗号分隔")
        String toolWhitelist,
        @Schema(description = "可读写文档范围，JSON 格式")
        String docScope,
        @Schema(description = "Token 预算上限")
        Long tokenBudget,
        @Schema(description = "状态：1 正常 / 0 禁用", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Integer status) {

    /**
     * 将更新字段应用到已有 Agent 实体。
     *
     * @param entity 已有 Agent 实体
     * @param encryptedMcpConfig 新的加密配置；为 null 时保留原配置
     */
    public void applyTo(AgentEntity entity, String encryptedMcpConfig) {
        entity.setName(name);
        entity.setDescription(description);
        entity.setClientId(clientId);
        entity.setModelId(modelId);
        if (encryptedMcpConfig != null) {
            entity.setMcpConfig(encryptedMcpConfig);
        }
        entity.setToolWhitelist(toolWhitelist);
        entity.setDocScope(docScope);
        entity.setTokenBudget(tokenBudget);
        entity.setStatus(status);
    }
}
