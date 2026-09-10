package com.agentdoc.task.pojo.vo;

import java.math.BigDecimal;

/**
 * Token 用量聚合数据库行。
 */
public record TokenUsageStatisticsRow(
        Long recordCount,
        Long inputTokens,
        Long outputTokens,
        BigDecimal estimatedCost,
        Long taskCount,
        Long missingInputCount,
        Long missingOutputCount,
        Long missingCostCount,
        Long estimatedInputCount,
        Long estimatedOutputCount) {
}

