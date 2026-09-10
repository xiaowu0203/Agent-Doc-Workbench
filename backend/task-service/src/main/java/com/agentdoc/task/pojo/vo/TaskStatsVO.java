package com.agentdoc.task.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 空间任务数量统计。
 *
 * @param totalCount 当前空间任务总数
 * @param countAsOfYesterday 截至昨日创建的任务数
 */
@Schema(description = "空间任务数量统计")
public record TaskStatsVO(
        @Schema(description = "当前空间任务总数") long totalCount,
        @Schema(description = "截至昨日创建的任务数") long countAsOfYesterday) {
}
