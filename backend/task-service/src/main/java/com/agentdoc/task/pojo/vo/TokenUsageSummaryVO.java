package com.agentdoc.task.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 指定时间范围 Token 用量摘要。
 */
@Schema(description = "Token 用量摘要")
public record TokenUsageSummaryVO(
        @Schema(description = "是否存在用量记录") boolean hasData,
        @Schema(description = "已知部分输入 Token 合计；全部未知时为 null") Long inputTokens,
        @Schema(description = "已知部分输出 Token 合计；全部未知时为 null") Long outputTokens,
        @Schema(description = "已知部分输入与输出 Token 合计；任一侧全部未知时为 null") Long tokens,
        @Schema(description = "已知部分预估费用合计；全部无法计价时为 null") BigDecimal estimatedCost,
        @Schema(description = "执行任务数") long executedTasks,
        @Schema(description = "工具调用数") long toolCalls,
        @Schema(description = "输入 Token 是否包含估算值") boolean inputTokensEstimated,
        @Schema(description = "输出 Token 是否包含估算值") boolean outputTokensEstimated,
        @Schema(description = "是否存在 Token 或成本缺失的明细记录") boolean hasIncompleteData) {
}
