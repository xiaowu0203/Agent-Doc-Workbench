package com.agentdoc.task.constant;

/**
 * 任务模块常量类
 * 存放任务执行、重试、分片、MCP调用、Token预算、统计趋势等相关固定参数
 */
public final class TaskConstant {

    private TaskConstant() {
    }

    /**
     * 任务分布式锁超时时间，单位：分钟
     */
    public static final int TASK_LOCK_TIMEOUT_MINUTES = 30;
    /**
     * 任务最大重试次数
     */
    public static final int MAX_TASK_RETRY_COUNT = 3;
    /**
     * 每次重试计数自增步长
     */
    public static final int RETRY_COUNT_INCREMENT = 1;
    /**
     * 错误信息最大保存长度，数据库字段长度预留
     */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 1900;
    /**
     * 分片任务初始起始偏移量
     */
    public static final long INITIAL_FRAGMENT_START = 0L;
    /**
     * 分片任务初始单次处理数量
     */
    public static final int INITIAL_FRAGMENT_LENGTH = 500;
    /**
     * MCP工具调用单次分片最大数据长度
     */
    public static final int MAX_MCP_FRAGMENT_LENGTH = 20_000;
    /**
     * 趋势统计最小天数
     */
    public static final int MIN_TREND_DAYS = 1;
    /**
     * 趋势统计最大天数
     */
    public static final int MAX_TREND_DAYS = 90;
    /** 用量统计统一使用的业务时区。 */
    public static final String TOKEN_USAGE_TIME_ZONE = "Asia/Shanghai";
    /**
     * 趋势统计默认查询天数，字符串类型用于配置默认值
     */
    public static final String DEFAULT_TREND_DAYS = "7";
    /**
     * Token计价单位，按百万Token计价(1_000_000)
     */
    public static final long TOKEN_PRICE_UNIT = 1_000_000L;
    /**
     * Token费用计算小数保留精度
     */
    public static final int TOKEN_COST_SCALE = 6;
    /**
     * MCP工具调用默认超时时间，单位：秒
     */
    public static final long DEFAULT_MCP_TIMEOUT_SECONDS = 60L;
    /**
     * MCP工具调用最小超时时间，单位：秒
     */
    public static final long MIN_MCP_TIMEOUT_SECONDS = 1L;
    /**
     * MCP工具调用最大超时时间，单位：秒
     */
    public static final long MAX_MCP_TIMEOUT_SECONDS = 600L;
    /**
     * 预估每个Token对应字符数，用于字符转Token粗略估算
     */
    public static final int ESTIMATED_CHARACTERS_PER_TOKEN = 4;
    /**
     * 预估Token数量下限，避免0值
     */
    public static final long MIN_ESTIMATED_TOKENS = 1L;
    /**
     * Token预算最小值，任务不能设置0预算
     */
    public static final long MIN_TOKEN_BUDGET = 1L;
    /**
     * 任务名称最大长度，与数据库 task.name 字段保持一致
     */
    public static final int MAX_TASK_NAME_LENGTH = 100;
    /**
     * 任务指令最大长度，限制表单输入和执行快照体积
     */
    public static final int MAX_TASK_INSTRUCTION_LENGTH = 4000;
    /**
     * 任务列表搜索关键字最大长度
     */
    public static final int MAX_TASK_SEARCH_KEYWORD_LENGTH = 100;
    /** 单个任务最多选择的关注区域数。 */
    public static final int MAX_TASK_FOCUS_REGION_COUNT = 20;
    /** 单个关注区域要求的最大长度。 */
    public static final int MAX_TASK_FOCUS_INSTRUCTION_LENGTH = 1000;
    /** 关注区域文本预览的最大长度。 */
    public static final int MAX_TASK_FOCUS_PREVIEW_LENGTH = 500;
    /** 变更摘要、审批意见和单条批注最大长度。 */
    public static final int MAX_CHANGE_REVIEW_TEXT_LENGTH = 500;
    /** 单次批量审批最多处理的请求数。 */
    public static final int MAX_CHANGE_REQUEST_BATCH_SIZE = 100;
    /** 单次部分接受最多记录的 Diff 块数量。 */
    public static final int MAX_ACCEPTED_CHANGE_KEY_COUNT = 500;
    /**
     * 可读任务编号前缀
     */
    public static final String TASK_NO_PREFIX = "T-";
    /**
     * 日期偏移量，用于日期计算偏移修正
     */
    public static final long DAY_OFFSET = 1L;
    /**
     * 快照生成默认延迟时间，单位：毫秒，字符串用于配置解析
     */
    public static final String DEFAULT_SNAPSHOT_DELAY_MILLIS = "180000";
    /**
     * A2A对账分布式锁持有超时，单位：秒，小于调度周期防止锁永久占用
     */
    public static final long A2A_RECONCILE_LOCK_SECONDS = 55L;
}
