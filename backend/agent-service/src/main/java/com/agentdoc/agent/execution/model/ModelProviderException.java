package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelErrorType;

/**
 * 模型供应商调用的统一异常。
 * <p>上层不需要依赖 Spring AI 或具体厂商 SDK，即可判断错误分类和是否允许重试。</p>
 */
public class ModelProviderException extends RuntimeException {

    private final String provider;
    private final ModelErrorType errorType;
    private final Integer statusCode;
    private final String providerCode;
    private final boolean retryable;

    public ModelProviderException(String provider, ModelErrorType errorType, Integer statusCode,
                                  String providerCode, boolean retryable, String message,
                                  Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.errorType = errorType;
        this.statusCode = statusCode;
        this.providerCode = providerCode;
        this.retryable = retryable;
    }

    public String getProvider() {
        return provider;
    }

    public ModelErrorType getErrorType() {
        return errorType;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
