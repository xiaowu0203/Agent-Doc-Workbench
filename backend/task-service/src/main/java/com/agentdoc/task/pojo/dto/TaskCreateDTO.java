package com.agentdoc.task.pojo.dto;

import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.pojo.entity.TaskEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.agentdoc.task.constant.TaskConstant.MAX_TASK_FOCUS_REGION_COUNT;
import static com.agentdoc.task.constant.TaskConstant.MAX_TASK_INSTRUCTION_LENGTH;
import static com.agentdoc.task.constant.TaskConstant.MAX_TASK_NAME_LENGTH;

/**
 * Agent 任务创建参数。
 */
@Schema(description = "Agent 任务创建参数")
public record TaskCreateDTO(
        @Schema(description = "所属空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long spaceId,
        @Schema(description = "Agent ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long agentId,
        @Schema(description = "目标文档 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long documentId,
        @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = MAX_TASK_NAME_LENGTH) String name,
        @Schema(description = "任务指令", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = MAX_TASK_INSTRUCTION_LENGTH) String instruction,
        @Schema(description = "Token 预算上限")
        @Positive Long tokenBudget,
        @Schema(description = "文档读取范围；为空时默认 FULL")
        TaskReadScope readScope,
        @Valid @Size(max = MAX_TASK_FOCUS_REGION_COUNT)
        @Schema(description = "文档关注区域；RANGES 模式下同时作为读取白名单")
        List<@NotNull TaskFocusRegionDTO> focusRegions) {

    /**
     * 转换为待执行任务实体。
     *
     * @param spaceId 任务所属空间 ID
     * @param documentType 创建任务时的文档类型快照
     * @param budget 校验并收敛后的 Token 预算
     * @param agentConfigVersion Agent 配置版本快照
     * @param effectiveReadScope 生效的读取范围类型
     * @param focusRegionsJson 关注区域 JSON
     * @param userId 创建人用户 ID
     * @return 初始化完成的待执行任务实体
     */
    public TaskEntity toEntity(Long spaceId, Integer documentType, Long budget, Long agentConfigVersion,
                               TaskReadScope effectiveReadScope, String focusRegionsJson,
                               Long userId) {
        TaskEntity entity = new TaskEntity();
        entity.setSpaceId(spaceId);
        entity.setAgentId(agentId);
        entity.setAgentConfigVersion(agentConfigVersion);
        entity.setDocumentId(documentId);
        entity.setDocumentType(documentType);
        entity.setName(name.trim());
        entity.setInstruction(instruction.trim());
        entity.setStatus(TaskStatus.PENDING.getCode());
        entity.setTokenBudget(budget);
        entity.setReadScope(effectiveReadScope.name());
        entity.setFocusRegionsJson(focusRegionsJson);
        entity.setTokensUsed(null);
        entity.setTokensEstimated(Boolean.FALSE);
        entity.setRetryCount(0);
        entity.setCreatedBy(userId);
        return entity;
    }
}
