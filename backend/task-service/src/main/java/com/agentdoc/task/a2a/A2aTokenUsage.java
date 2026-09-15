package com.agentdoc.task.a2a;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record A2aTokenUsage(
        Long inputTokens,
        Long cachedInputTokens,
        Long outputTokens,
        // 预估输入Token
        boolean inputTokensEstimated,
        // 预估缓存Token
        boolean cachedInputTokensEstimated,
        // 预估输出Token
        boolean outputTokensEstimated,
        Long executionId,
        Long modelId,
        Long modelConfigVersion,
        BigDecimal inputPricePerMillion,
        BigDecimal outputPricePerMillion,
        String currency,
        Integer pricingSchemaVersion,
        LocalDateTime pricingCapturedAt) {

    public A2aTokenUsage(Long inputTokens, Long cachedInputTokens, Long outputTokens) {
        this(inputTokens, cachedInputTokens, outputTokens, false, false, false,
                null, null, null, null, null, null, null, null);
    }

    public A2aTokenUsage(Long inputTokens, Long cachedInputTokens, Long outputTokens,
                         boolean inputTokensEstimated, boolean cachedInputTokensEstimated,
                         boolean outputTokensEstimated) {
        this(inputTokens, cachedInputTokens, outputTokens, inputTokensEstimated, cachedInputTokensEstimated,
                outputTokensEstimated, null, null, null, null, null, null, null, null);
    }
}
