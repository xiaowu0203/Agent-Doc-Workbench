package com.agentdoc.task.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import static com.agentdoc.task.constant.TaskConstant.MAX_TASK_FOCUS_INSTRUCTION_LENGTH;
import static com.agentdoc.task.constant.TaskConstant.MAX_TASK_FOCUS_PREVIEW_LENGTH;

/** 文档关注区域及该区域的独立处理要求。 */
@Schema(description = "任务文档关注区域")
public record TaskFocusRegionDTO(
        @NotNull @PositiveOrZero @Schema(description = "起始字符偏移") Long start,
        @NotNull @Positive @Schema(description = "字符数量") Long length,
        @Size(max = MAX_TASK_FOCUS_PREVIEW_LENGTH) @Schema(description = "选中文本预览") String textPreview,
        @Size(max = MAX_TASK_FOCUS_INSTRUCTION_LENGTH) @Schema(description = "区域处理要求") String instruction) {
}
