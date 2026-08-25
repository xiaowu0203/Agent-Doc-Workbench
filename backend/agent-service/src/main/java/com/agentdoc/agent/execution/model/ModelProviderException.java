package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelErrorType;

/**
 * 模型厂商调用异常
 * <p>
 * 由{@link AbstractSpringAiModelAdapter#translateException}翻译生成，
 * 将各个大模型SDK抛出的原始异常统一转换为此业务异常。
 * 上层Agent Runtime、业务服务可以读取异常字段，做统一的错误分类、重试判断、告警、前端提示。
 * </p>
 * <p>
 * 字段含义：记录厂商、错误类型、HTTP状态码、厂商原始错误码、是否可重试、原始cause堆栈。
 * 不直接向外暴露SDK原生异常信息，避免密钥、内部url等敏感信息泄漏。
 * </p>
 */
public class ModelProviderException extends RuntimeException {

    // /** 模型厂商标识，例如 openai / anthropic / google */
    private final String provider;
    /** 标准化错误类型，用于业务逻辑分支判断：鉴权、限流、上下文超长、超时等 */
    private final ModelErrorType errorType;
    /** HTTP响应状态码，可为null（非HTTP异常时为空） */
    private final Integer statusCode;
    /** 厂商侧原始错误码/异常类名，用于日志排查，非面向用户展示 */
    private final String providerCode;
    /** 是否允许重试；true代表瞬时故障，可以进行重试；false代表业务类错误，禁止重试 */
    private final boolean retryable;

    /**
     * 构造模型厂商调用异常
     * @param provider 模型厂商名称
     * @param errorType 标准化错误枚举类型
     * @param statusCode HTTP状态码，无则传null
     * @param providerCode 厂商原始错误码或者异常简单类名，用于日志排查
     * @param retryable true=瞬时故障可重试；false=不可重试
     * @param message 对外展示的异常描述信息
     * @param cause 底层原始SDK/HTTP异常，保留堆栈用于日志排查
     */
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
