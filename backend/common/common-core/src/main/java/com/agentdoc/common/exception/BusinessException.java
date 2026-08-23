package com.agentdoc.common.exception;

import com.agentdoc.common.enums.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 业务异常，全局异常处理器统一转换为 Result。
 * <p>普通接口由全局异常处理器输出 HTTP 200 + 业务 code；
 * 服务间调用等需要 HTTP 状态码的场景可用 {@link #getHttpStatus()} 映射（见 {@link ErrorCode#getHttpStatus()}）。</p>
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 业务错误码映射的 HTTP 状态（未知错误码默认 500）。
     * @return HTTP 状态
     */
    public HttpStatus getHttpStatus() {
        ErrorCode errorCode = ErrorCode.fromCode(code);
        return errorCode == null ? HttpStatus.INTERNAL_SERVER_ERROR : errorCode.getHttpStatus();
    }

    public int getCode() {
        return code;
    }
}
