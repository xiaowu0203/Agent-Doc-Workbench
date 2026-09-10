package com.agentdoc.task.pojo.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Token 用量每日聚合数据库行。
 */
public record TokenUsageDailyRow(
        LocalDate usageDate,
        Long recordCount,
        Long inputTokens,
        Long outputTokens,
        BigDecimal estimatedCost,
        Long missingInputCount,
        Long missingOutputCount,
        Long missingCostCount,
        Long estimatedInputCount,
        Long estimatedOutputCount) {
}

