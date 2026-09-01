package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.DocumentActivityType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文档相关活动聚合项。
 */
@Schema(description = "文档活动")
public record DocumentActivityVO(

        @Schema(description = "活动 ID")
        Long id,

        @Schema(description = "活动类型")
        DocumentActivityType type,

        @Schema(description = "活动标题")
        String title,

        @Schema(description = "活动状态展示名称")
        String status,

        @Schema(description = "关联任务 ID；变更请求由 Agent 任务产生时存在")
        Long sourceTaskId,

        @Schema(description = "操作人名称")
        String operatorName,

        @Schema(description = "活动时间")
        LocalDateTime activityAt
) {
}
