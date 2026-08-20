package com.agentdoc.common.constant;

/**
 * Redis 键前缀常量：统一以工程名前缀开头，避免与共享 Redis 实例上其他项目的键冲突。
 * 使用约定：所有写入 Redis 的键必须通过本类组合生成（禁止硬编码裸键）。
 */
public final class RedisKeyConstants {

    /** 工程级前缀：所有 Redis 键统一以此开头 */
    public static final String PROJECT_PREFIX = "agent-doc-workbench";

    /** Refresh Token 键前缀：agent-doc-workbench:auth:refresh: */
    public static final String REFRESH_TOKEN_PREFIX = PROJECT_PREFIX + ":auth:refresh:";

    private RedisKeyConstants() {
    }
}
