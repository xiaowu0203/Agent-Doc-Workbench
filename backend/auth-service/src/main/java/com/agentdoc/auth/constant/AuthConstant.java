package com.agentdoc.auth.constant;

/**
 * 认证域常量。
 */
public final class AuthConstant {

    private AuthConstant() {
    }

    /** 临时 RSA 密钥位数 */
    public static final int RSA_KEY_SIZE = 2048;

    /** Refresh Token 随机字节长度 */
    public static final int REFRESH_TOKEN_BYTE_LENGTH = 48;

    /** 任务能力令牌有效期（小时） */
    public static final long TASK_CAPABILITY_TTL_HOURS = 6L;

    /** PEM 私钥开始标记 */
    public static final String PEM_PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";

    /** PEM 私钥结束标记 */
    public static final String PEM_PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    /** PEM 公钥开始标记 */
    public static final String PEM_PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";

    /** PEM 公钥结束标记 */
    public static final String PEM_PUBLIC_KEY_END = "-----END PUBLIC KEY-----";
}
