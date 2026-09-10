package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 执行动态列表项。
 */
@Schema(description = "任务执行动态")
public record TaskActivityVO(

        @Schema(description = "任务 ID")
        Long id,

        @Schema(description = "任务名称")
        String name,

        @Schema(description = "Agent ID")
        Long agentId,

        @Schema(description = "任务状态")
        TaskStatus status,

        @Schema(description = "操作人名称")
        String operatorName,

        @Schema(description = "最近执行时间")
        LocalDateTime activityAt
) {
}
