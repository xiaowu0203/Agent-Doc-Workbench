package com.agentdoc.task.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 从 Token 明细按统计维度聚合出的内部行。
 */
@Schema(description = "Token 用量聚合内部行")
public record TokenUsageAggregateRow(
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "统计对象 ID") Long objId,
        @Schema(description = "Token 数量") Long tokens) {
}
