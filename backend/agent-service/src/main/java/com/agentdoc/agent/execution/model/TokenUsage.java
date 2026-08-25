package com.agentdoc.agent.execution.model;

import com.agentdoc.common.pojo.TokenValue;

/** 一次或多次模型调用的 Token 用量，按字段保留数值来源。 */
public record TokenUsage(
        TokenValue input,
        TokenValue cachedInput,
        TokenValue output) {

    public static TokenUsage unavailable() {
        return new TokenUsage(TokenValue.unavailable(), TokenValue.unavailable(), TokenValue.unavailable());
    }

    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(TokenValue.add(input, other.input),
                TokenValue.add(cachedInput, other.cachedInput),
                TokenValue.add(output, other.output));
    }
}
