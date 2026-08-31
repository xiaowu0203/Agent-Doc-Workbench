package com.agentdoc.task.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 空间待审批变更数量统计。
 *
 * @param pendingCount 当前待审批变更数
 * @param pendingCountAsOfYesterday 截至昨日创建且当前仍待审批的变更数
 */
@Schema(description = "空间待审批变更数量统计")
public record PendingChangeStatsVO(
        @Schema(description = "当前待审批变更数") long pendingCount,
        @Schema(description = "截至昨日创建且当前仍待审批的变更数") long pendingCountAsOfYesterday) {
}
