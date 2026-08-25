package com.agentdoc.common.pojo;

import com.agentdoc.common.enums.TokenValueSource;

/** 带来源标记的可空 Token 数值。 */
public record TokenValue(Long value, TokenValueSource source) {

    public TokenValue {
        if (value == null && source != TokenValueSource.UNAVAILABLE) {
            throw new IllegalArgumentException("无 Token 数值时来源必须为 UNAVAILABLE");
        }
        if (value != null && source == TokenValueSource.UNAVAILABLE) {
            throw new IllegalArgumentException("有 Token 数值时来源不能为 UNAVAILABLE");
        }
    }

    public static TokenValue provider(Long value) {
        return value == null ? unavailable() : new TokenValue(value, TokenValueSource.PROVIDER);
    }

    public static TokenValue estimated(Long value) {
        return value == null ? unavailable() : new TokenValue(value, TokenValueSource.ESTIMATED);
    }

    public static TokenValue unavailable() {
        return new TokenValue(null, TokenValueSource.UNAVAILABLE);
    }

    public boolean available() {
        return value != null;
    }

    public boolean estimated() {
        return source == TokenValueSource.ESTIMATED;
    }

    public static TokenValue add(TokenValue left, TokenValue right) {
        if (!left.available() || !right.available()) {
            return unavailable();
        }
        TokenValueSource source = left.source == TokenValueSource.PROVIDER
                && right.source == TokenValueSource.PROVIDER
                ? TokenValueSource.PROVIDER : TokenValueSource.ESTIMATED;
        return new TokenValue(left.value + right.value, source);
    }
}
