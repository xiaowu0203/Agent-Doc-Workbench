package com.agentdoc.common.enums;

/**
 * 统一错误码。
 * 规则：0 成功；4xxxx 客户端错误；5xxxx 服务端错误。
 * 两位段：前两位为域，后三位为具体错误。
 */
public enum ErrorCode {

    SUCCESS(0, "success"),

    // 通用 400
    BAD_REQUEST(40000, "请求参数错误"),
    VALIDATION_FAILED(40001, "参数校验失败"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    TOKEN_EXPIRED(40101, "访问令牌已过期"),
    FORBIDDEN(40300, "无权限访问"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),
    CONFLICT(40900, "资源状态冲突"),
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),

    // 认证域 41xxx
    USERNAME_EXISTS(41001, "用户名已存在"),
    LOGIN_FAILED(41002, "用户名或密码错误"),
    USER_DISABLED(41003, "账号已被禁用"),
    REFRESH_TOKEN_INVALID(41004, "刷新令牌无效或已过期"),

    // 服务端 500
    INTERNAL_ERROR(50000, "服务器内部错误"),
    SERVICE_UNAVAILABLE(50300, "服务暂不可用");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
