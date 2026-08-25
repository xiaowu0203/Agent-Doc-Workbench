package com.agentdoc.agent.enums;

/** 模型调用的统一错误分类。 */
public enum ModelErrorType {
    AUTHENTICATION,
    RATE_LIMIT,
    CONTEXT_LENGTH,
    INVALID_REQUEST,
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    PROVIDER_ERROR
}
