package com.agentdoc.common.feign.config;

import java.time.Duration;

/**
 * P1认证依赖的固定初始预算；放宽预算须同步设计及故障验证。
 * @param connectTimeout 连接及TLS握手时限
 * @param readTimeout 单次响应读取等待时限
 * @param totalTimeout 包括DNS、连接及完整响应体的调用方等待上限
 * @param maxInFlight 每进程准入及连接池上限
 */
public record AuthenticationHttpBudget(Duration connectTimeout, Duration readTimeout,
                                       Duration totalTimeout, int maxInFlight) {

    public static final AuthenticationHttpBudget IDENTITY = new AuthenticationHttpBudget(
            Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(8), 32);
    public static final AuthenticationHttpBudget INTROSPECTION = new AuthenticationHttpBudget(
            Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4), 16);

    public AuthenticationHttpBudget {
        if (connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()
                || totalTimeout.compareTo(connectTimeout) < 0 || totalTimeout.compareTo(readTimeout) < 0 || maxInFlight < 1) {
            throw new IllegalArgumentException("认证依赖预算无效");
        }
    }
}
