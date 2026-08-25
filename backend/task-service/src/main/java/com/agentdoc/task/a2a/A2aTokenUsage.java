package com.agentdoc.task.a2a;

public record A2aTokenUsage(
        long inputTokens,
        long cachedInputTokens,
        long outputTokens) {
}
