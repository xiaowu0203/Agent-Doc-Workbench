package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.enums.DocType;
import com.agentdoc.task.enums.TaskReadScope;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Agent 获取的任务文档上下文，包含本次任务固化的读取边界。
 */
@Schema(description = "任务文档执行上下文")
public record TaskDocumentContextVO(
        @Schema(description = "任务 ID") Long taskId,
        @Schema(description = "任务编号") String taskNo,
        @Schema(description = "文档 ID") Long documentId,
        @Schema(description = "空间 ID") Long spaceId,
        @Schema(description = "文档类型") DocType documentType,
        @Schema(description = "文档版本") Long documentVersion,
        @Schema(description = "文档字符数") Long documentLength,
        @Schema(description = "允许读取的范围") TaskReadScope readScope,
        @Schema(description = "关注区域；RANGES 时同时作为读取白名单") List<TaskFocusRegionVO> focusRegions) {
}
