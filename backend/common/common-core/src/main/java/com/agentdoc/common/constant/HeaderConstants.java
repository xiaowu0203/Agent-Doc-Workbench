package com.agentdoc.common.constant;

/**
 * 网关与业务服务之间透传的请求头常量。
 * 网关从 JWT 解析后写入，业务服务通过 UserContextFilter 读取。
 */
public final class HeaderConstants {

    private HeaderConstants() {
    }

    /** 用户 ID */
    public static final String X_USER_ID = "X-User-Id";
    /** 用户名 */
    public static final String X_USER_NAME = "X-User-Name";
    /** 昵称 */
    public static final String X_USER_NICKNAME = "X-User-Nickname";
    /** Agent ID（外部 Agent 访问时） */
    public static final String X_AGENT_ID = "X-Agent-Id";
    /** 权限 scope，逗号分隔 */
    public static final String X_USER_SCOPES = "X-User-Scopes";
    /** 链路追踪 ID */
    public static final String X_TRACE_ID = "X-Trace-Id";
}
