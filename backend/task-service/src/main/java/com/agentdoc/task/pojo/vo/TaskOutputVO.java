package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.TaskOutputType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务执行产生的业务结果引用。
 */
@Schema(description = "任务执行结果引用")
public record TaskOutputVO(
        @Schema(description = "结果类型") TaskOutputType type,
        @Schema(description = "结果资源 ID") Long id,
        @Schema(description = "结果资源状态；草稿文档结果为空") String status,
        @Schema(description = "目标文档 ID") Long documentId) {
}
