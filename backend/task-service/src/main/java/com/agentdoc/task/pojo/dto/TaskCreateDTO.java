package com.agentdoc.task.pojo.dto;

import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.pojo.entity.TaskEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Agent 任务创建参数。
 */
@Schema(description = "Agent 任务创建参数")
public record TaskCreateDTO(
        @Schema(description = "Agent ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long agentId,
        @Schema(description = "目标文档 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long documentId,
        @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,
        @Schema(description = "任务指令", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String instruction,
        @Schema(description = "Token 预算上限")
        Long tokenBudget) {

    /**
     * 转换为待执行任务实体。
     *
     * @param spaceId 任务所属空间 ID
     * @param budget 校验并收敛后的 Token 预算
     * @param userId 创建人用户 ID
     * @return 初始化完成的待执行任务实体
     */
    public TaskEntity toEntity(Long spaceId, Long budget, Long agentConfigVersion, Long userId) {
        TaskEntity entity = new TaskEntity();
        entity.setSpaceId(spaceId);
        entity.setAgentId(agentId);
        entity.setAgentConfigVersion(agentConfigVersion);
        entity.setDocumentId(documentId);
        entity.setName(name);
        entity.setInstruction(instruction);
        entity.setStatus(TaskStatus.PENDING.getCode());
        entity.setTokenBudget(budget);
        entity.setTokensUsed(0L);
        entity.setRetryCount(0);
        entity.setCreatedBy(userId);
        return entity;
    }
}
