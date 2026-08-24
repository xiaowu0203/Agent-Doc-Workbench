package com.agentdoc.task.constant;

/**
 * 任务执行、Token 统计与 MCP 响应处理相关常量。
 */
public final class TaskConstant {

    private TaskConstant() {
    }

    public static final int TASK_LOCK_TIMEOUT_MINUTES = 30;
    public static final int MAX_TASK_RETRY_COUNT = 3;
    public static final int RETRY_COUNT_INCREMENT = 1;
    public static final int MAX_ERROR_MESSAGE_LENGTH = 1900;
    public static final long INITIAL_FRAGMENT_START = 0L;
    public static final int INITIAL_FRAGMENT_LENGTH = 500;
    public static final int MIN_TREND_DAYS = 1;
    public static final int MAX_TREND_DAYS = 90;
    public static final String DEFAULT_TREND_DAYS = "7";
    public static final long TOKEN_PRICE_UNIT = 1_000_000L;
    public static final int TOKEN_COST_SCALE = 6;
    public static final long DEFAULT_MCP_TIMEOUT_SECONDS = 60L;
    public static final long MIN_MCP_TIMEOUT_SECONDS = 1L;
    public static final long MAX_MCP_TIMEOUT_SECONDS = 600L;
    public static final int ESTIMATED_CHARACTERS_PER_TOKEN = 4;
    public static final long MIN_ESTIMATED_TOKENS = 1L;
    public static final long MIN_TOKEN_BUDGET = 1L;
    public static final long DAY_OFFSET = 1L;
    public static final String DEFAULT_SNAPSHOT_DELAY_MILLIS = "180000";
}
