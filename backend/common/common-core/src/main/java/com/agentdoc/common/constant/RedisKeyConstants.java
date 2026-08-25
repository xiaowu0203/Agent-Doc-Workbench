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

    /**
     * Refresh Token 用户索引键前缀：agent-doc-workbench:auth:refresh:user:
     * 存放 {@code userId -> 当前有效refreshToken} 的反查映射，与 {@link #REFRESH_TOKEN_PREFIX}
     * 的 token 主映射键空间隔离，避免 token 字符串与数字 userId 落入同一命名空间产生撞键风险。
     */
    public static final String REFRESH_TOKEN_USER_INDEX_PREFIX = PROJECT_PREFIX + ":auth:refresh:user:";

    /** 请求限流计数器键前缀：agent-doc-workbench:rate */
    public static final String RATE_KEY_PREFIX = RedisKeyConstants.PROJECT_PREFIX + ":rate:";

    /** 同一空间 Agent 任务串行执行锁 */
    public static final String TASK_SPACE_LOCK_PREFIX = PROJECT_PREFIX + ":task:space:lock:";

    /** A2A 任务状态对账锁 */
    public static final String TASK_A2A_RECONCILE_LOCK_PREFIX = PROJECT_PREFIX + ":task:a2a:reconcile:lock:";

    private RedisKeyConstants() {
    }
}
