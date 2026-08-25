package com.agentdoc.agent.enums;

/** 模型调用的统一错误分类。 */
public enum ModelErrorType {
    /**
     * 鉴权失败：API‑Key错误、密钥过期、权限不足，对应HTTP 401 / 403
     * <p>属于配置错误，不可重试。</p>
     */
    AUTHENTICATION,
    /**
     * 请求被限流：调用频率超限，对应HTTP 429
     * <p>瞬时故障，支持延迟重试。</p>
     */
    RATE_LIMIT,
    /**
     * 上下文长度超限：输入+历史消息总token超过模型最大窗口
     * <p>业务输入问题，不可重试。</p>
     */
    CONTEXT_LENGTH,
    /**
     * 请求参数非法：参数格式错误、字段不合法，对应HTTP 400
     * <p>业务请求错误，不可重试。</p>
     */
    INVALID_REQUEST,
    /**
     * 请求超时：网络超时、模型响应超时，对应408 / 504
     * <p>瞬时网络故障，支持重试。</p>
     */
    TIMEOUT,
    /**
     * 厂商服务不可用：网络异常、连接失败、厂商5xx服务故障
     * <p>瞬时故障，支持重试。</p>
     */
    PROVIDER_UNAVAILABLE,
    /**
     * 厂商未归类的其他未知内部错误
     * <p>根据场景，可酌情开启有限次数重试。</p>
     */
    PROVIDER_ERROR
}
