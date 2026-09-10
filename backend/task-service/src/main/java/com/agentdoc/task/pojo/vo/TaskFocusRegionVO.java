package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.pojo.dto.TaskFocusRegionDTO;
import io.swagger.v3.oas.annotations.media.Schema;

/** 任务关注区域视图。 */
@Schema(description = "任务关注区域")
public record TaskFocusRegionVO(
        @Schema(description = "起始字符偏移") Long start,
        @Schema(description = "字符数量") Long length,
        @Schema(description = "选中文本预览") String textPreview,
        @Schema(description = "区域处理要求") String instruction) {

    public static TaskFocusRegionVO from(TaskFocusRegionDTO region) {
        return new TaskFocusRegionVO(region.start(), region.length(), region.textPreview(), region.instruction());
    }
}
