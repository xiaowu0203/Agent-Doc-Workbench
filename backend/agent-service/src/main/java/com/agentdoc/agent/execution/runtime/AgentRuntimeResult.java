package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.execution.model.TokenUsage;
public record AgentRuntimeResult(
        String summary,
        TokenUsage tokenUsage) {
}
