package com.agentdoc.common.exception;

import com.agentdoc.common.enums.ErrorCode;
import lombok.Getter;

/** 人类授权复核失败；仅携带固定分类，不保留远程异常、请求或凭据。 */
@Getter
public final class HumanAuthorizationException extends RuntimeException {
    /** 身份失效或依赖不可用分类，用于保留方法鉴权包装后的HTTP语义。 */
    private final ErrorCode errorCode;

    public HumanAuthorizationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
