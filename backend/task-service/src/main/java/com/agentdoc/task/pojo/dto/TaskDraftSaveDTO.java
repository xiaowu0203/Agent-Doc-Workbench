package com.agentdoc.task.pojo.dto;

import com.agentdoc.task.enums.TaskReadScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.agentdoc.task.constant.TaskConstant.MAX_TASK_FOCUS_REGION_COUNT;
import static com.agentdoc.task.constant.TaskConstant.MAX_TASK_INSTRUCTION_LENGTH;
import static com.agentdoc.task.constant.TaskConstant.MAX_TASK_NAME_LENGTH;

/**
 * 保存任务草稿参数；除空间外均允许暂时为空。
 */
@Schema(description = "任务草稿保存参数")
public record TaskDraftSaveDTO(
        @NotNull @Schema(description = "所属空间 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long spaceId,
        @Schema(description = "Agent ID") Long agentId,
        @Schema(description = "目标文档 ID") Long documentId,
        @Size(max = MAX_TASK_NAME_LENGTH) @Schema(description = "任务名称") String name,
        @Size(max = MAX_TASK_INSTRUCTION_LENGTH) @Schema(description = "任务指令") String instruction,
        @Positive @Schema(description = "任务 Token 预算") Long tokenBudget,
        @Schema(description = "读取范围；为空时默认 FULL") TaskReadScope readScope,
        @Valid @Size(max = MAX_TASK_FOCUS_REGION_COUNT)
        @Schema(description = "文档关注区域") List<@NotNull TaskFocusRegionDTO> focusRegions) {
}
