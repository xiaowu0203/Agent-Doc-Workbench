package com.agentdoc.agent.execution.runtime;

import com.agentdoc.agent.execution.model.TokenUsage;

/** 携带终止前累计 Token 用量的运行时异常。 */
public class AgentExecutionTerminatedException extends RuntimeException {

    private final TokenUsage tokenUsage;

    public AgentExecutionTerminatedException(RuntimeException cause, TokenUsage tokenUsage) {
        super(cause.getMessage(), cause);
        this.tokenUsage = tokenUsage;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public RuntimeException originalCause() {
        return (RuntimeException) getCause();
    }
}
