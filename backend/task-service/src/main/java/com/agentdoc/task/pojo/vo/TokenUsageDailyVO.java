package com.agentdoc.task.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Token 用量趋势中的单个自然日。
 */
@Schema(description = "Token 每日用量")
public record TokenUsageDailyVO(
        @Schema(description = "统计日期") LocalDate usageDate,
        @Schema(description = "当日是否存在用量记录") boolean hasData,
        @Schema(description = "已知部分输入 Token 合计；全部未知时为 null") Long inputTokens,
        @Schema(description = "已知部分输出 Token 合计；全部未知时为 null") Long outputTokens,
        @Schema(description = "已知部分总 Token；任一侧全部未知时为 null") Long tokens,
        @Schema(description = "已知部分预估费用合计；全部无法计价时为 null") BigDecimal estimatedCost,
        @Schema(description = "输入是否包含估算值") boolean inputTokensEstimated,
        @Schema(description = "输出是否包含估算值") boolean outputTokensEstimated,
        @Schema(description = "是否存在 Token 或成本缺失的明细记录") boolean hasIncompleteData) {
}
