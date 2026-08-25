package com.agentdoc.agent.execution;

public record AgentRuntimeResult(
        String summary,
        long inputTokens,
        Long cachedInputTokens,
        long outputTokens) {
}
