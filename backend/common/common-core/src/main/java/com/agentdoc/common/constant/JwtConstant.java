package com.agentdoc.common.constant;

/**
 * JWT Payload 声明键常量：auth-service（签发）与网关/业务服务（解析）共用，
 * 避免 claim 键名、scope 等魔法字符串在签发方与解析方各自硬编码导致拼写漂移。
 */
public final class JwtConstant {

    private JwtConstant() {
    }

    /** 声明键：用户名 */
    public static final String CLAIM_USERNAME = "username";

    /** 声明键：用户昵称 */
    public static final String CLAIM_NICKNAME = "nickname";

    /** 声明键：权限作用域（逗号分隔） */
    public static final String CLAIM_SCOPE = "scope";

    /** 声明键：Agent ID（外部 Agent 访问场景） */
    public static final String CLAIM_AGENT_ID = "agentId";

    /** 默认用户权限作用域 */
    public static final String SCOPE_USER = "user";

    /** Authorization 头 Bearer 令牌类型 */
    public static final String TOKEN_TYPE_BEARER = "Bearer";
}
