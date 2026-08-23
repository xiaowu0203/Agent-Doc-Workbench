package com.agentdoc.common.enums;

import org.springframework.http.HttpStatus;

/**
 * 统一错误码。
 * <p>规则：0 成功；4xxxx 客户端错误；5xxxx 服务端错误。
 * 两位段：前两位为域，后三位为具体错误。</p>
 * <p>每个错误码携带对应 HTTP 状态（{@link #getHttpStatus()}），供服务间调用（Feign 按状态识别）
 * 等需要 HTTP 状态码的场景使用；普通业务接口仍统一 HTTP 200 + code（由全局异常处理器处理）。</p>
 */
public enum ErrorCode {

    SUCCESS(0, "success", HttpStatus.OK),

    // 通用 400
    BAD_REQUEST(40000, "请求参数错误", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(40101, "访问令牌已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "无权限访问", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(40900, "资源状态冲突", HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(42900, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),

    // 认证域 41xxx
    USERNAME_EXISTS(41001, "用户名已存在", HttpStatus.CONFLICT),
    LOGIN_FAILED(41002, "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    USER_DISABLED(41003, "账号已被禁用", HttpStatus.FORBIDDEN),
    REFRESH_TOKEN_INVALID(41004, "刷新令牌无效或已过期", HttpStatus.UNAUTHORIZED),

    // 服务端 500
    INTERNAL_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(50300, "服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /**
     * 按业务错误码反查枚举，未知编码返回 null。
     * @param code 业务错误码
     * @return 对应枚举，未知返回 null
     */
    public static ErrorCode fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return null;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
