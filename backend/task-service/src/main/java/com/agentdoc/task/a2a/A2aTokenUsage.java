package com.agentdoc.task.a2a;

public record A2aTokenUsage(
        Long inputTokens,
        Long cachedInputTokens,
        Long outputTokens,
        // 预估输入Token
        boolean inputTokensEstimated,
        // 预估缓存Token
        boolean cachedInputTokensEstimated,
        // 预估输出Token
        boolean outputTokensEstimated) {

    public A2aTokenUsage(Long inputTokens, Long cachedInputTokens, Long outputTokens) {
        this(inputTokens, cachedInputTokens, outputTokens, false, false, false);
    }
}
