package com.agentdoc.task.pojo.vo;

import java.math.BigDecimal;

/**
 * 按空间汇总的 Token 当日快照行。
 */
public record TokenUsageSnapshotRow(
        Long spaceId,
        Long inputTokens,
        Long outputTokens,
        BigDecimal cost,
        boolean nullCost) {
}
