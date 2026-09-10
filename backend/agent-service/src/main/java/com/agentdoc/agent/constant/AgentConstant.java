package com.agentdoc.agent.constant;

/**
 * Agent‑service 模块常量定义
 * <p>
 * 存放Agent执行、参数校验、MCP客户端、配置版本相关常量；
 * 包含迭代次数、执行超时、Token预算上下限、MCP客户端标识等。
 * </p>
 */
public final class AgentConstant {

    /**
     * Agent默认最大工具迭代轮次，大模型工具调用循环最多执行次数
     */
    public static final int DEFAULT_MAX_ITERATIONS = 12;
    /**
     * Agent单次任务默认执行超时时间，单位：秒，默认10分钟
     */
    public static final int DEFAULT_EXECUTION_TIMEOUT_SECONDS = 600;
    /**
     * Token预算允许的最小阈值
     */
    public static final long MIN_TOKEN_BUDGET = 1L;
    /**
     * Agent 列表搜索关键字最大长度
     */
    public static final int MAX_SEARCH_KEYWORD_LENGTH = 100;
    /**
     * 最大迭代轮次参数校验：最小值
     */
    public static final int MIN_MAX_ITERATIONS = 1;
    /**
     * 最大迭代轮次参数校验：最大值，防止无限循环
     */
    public static final int MAX_MAX_ITERATIONS = 100;
    /**
     * 任务执行超时参数校验：最小超时，单位秒
     */
    public static final int MIN_EXECUTION_TIMEOUT_SECONDS = 10;
    /**
     * 任务执行超时参数校验：最大超时，单位秒，上限1小时
     */
    public static final int MAX_EXECUTION_TIMEOUT_SECONDS = 3600;
    /**
     * Agent配置记录初始版本号
     */
    public static final long INITIAL_CONFIG_VERSION = 1L;
    /**
     * Agent 配置版本号自增步长，更新配置时版本 +1，供运行时识别配置变化
     */
    public static final long CONFIG_VERSION_INCREMENT = 1L;
    /**
     * 错误信息最大截取长度，避免异常消息报文过大
     */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 2000;
    /**
     * MCP服务端默认端点路径，对应workbench暴露的MCP接口地址
     */
    public static final String DEFAULT_MCP_ENDPOINT = "/mcp";
    /**
     * MCP客户端名称标识，在MCP握手阶段上报给MCP‑Server(workbench)
     */
    public static final String MCP_CLIENT_NAME = "agent-doc-agent-service";
    /**
     * MCP客户端版本号，握手阶段上报
     */
    public static final String MCP_CLIENT_VERSION = "0.1.0";

    private AgentConstant() {
    }
}
