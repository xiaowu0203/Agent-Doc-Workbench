package com.agentdoc.task.pojo.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 查询任务创建选项的参数。
 */
@Schema(description = "任务创建选项查询参数")
public record TaskCreateOptionsParam(
        @NotNull @Schema(description = "所属空间 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long spaceId,
        @NotNull @Schema(description = "目标文档 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long documentId) {
}
