package com.agentdoc.common.feign.enums;

import java.util.Locale;

/** 认证依赖指标的固定低基数结果分类。 */
public enum AuthenticationHttpOutcome {
    /** 完整收到HTTP响应，业务结果由上层解释。 */
    COMPLETED,
    /** 连接、读取或总预算超时。 */
    TIMEOUT,
    /** 传输已关闭或达到准入上限。 */
    REJECTED,
    /** 调用方取消等待。 */
    INTERRUPTED,
    /** 其他依赖网络失败。 */
    FAILURE;

    public String metricValue() { return name().toLowerCase(Locale.ROOT); }
}
