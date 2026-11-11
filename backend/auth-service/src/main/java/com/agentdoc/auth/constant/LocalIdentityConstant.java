package com.agentdoc.auth.constant;

import java.time.Duration;

/** 本地认证的固定限流及内部协议约束。 */
public final class LocalIdentityConstant {
    private LocalIdentityConstant() { }

    /** 同一规范化账号的失败/预占上限。 */
    public static final int ACCOUNT_FAILURE_LIMIT = 5;
    /** 账号滑动窗口。 */
    public static final Duration ACCOUNT_FAILURE_WINDOW = Duration.ofMinutes(15);
    /** 同一受信客户端IP的失败/预占上限。 */
    public static final int IP_FAILURE_LIMIT = 50;
    /** IP滑动窗口。 */
    public static final Duration IP_FAILURE_WINDOW = Duration.ofMinutes(5);
    /** 默认共享Redis键空间，所有Auth实例必须一致。 */
    public static final String LOGIN_RATE_NAMESPACE = "agent-doc-workbench:auth:local-login";
    /** 受限BFF人类签发路径。 */
    public static final String HUMAN_CREDENTIAL_PATH = "/api/auth/internal/local-human-credentials";
    /** 受限服务证明签发路径。 */
    public static final String SERVICE_PROOF_PATH = "/api/auth/internal/service-proofs";
    /** 机器认证头的最大字符数。 */
    public static final int MAX_CLIENT_AUTHORIZATION_LENGTH = 4096;
    /** BCrypt输入的UTF8字节上限，不能与字符数混用。 */
    public static final int MAX_PASSWORD_UTF8_BYTES = 72;
    /** 未认证/机器认证事件的系统主体类型，不可用于用户授权。 */
    public static final int AUTHENTICATION_SYSTEM_ACTOR = 3;
    /** 认证系统保留主体ID；正式人类凭据只接受正数user.id。 */
    public static final long AUTHENTICATION_SYSTEM_ID = 0;
    /** 认证审计目标类型。 */
    public static final String AUDIT_TARGET = "AUTHENTICATION";
}
