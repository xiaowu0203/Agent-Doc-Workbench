package com.agentdoc.agent.constant;

/**
 * MCP 固定协议与安全约束。
 */
public final class McpConstant {

    /** 单个 Agent 最多绑定的外部 MCP 数量。 */
    public static final int MAX_BINDINGS_PER_AGENT = 10;
    /** 单条绑定最多配置的工具白名单数量。 */
    public static final int MAX_TOOL_WHITELIST_SIZE = 100;
    /** MCP Server 技术标识最大字符数。 */
    public static final int MAX_SERVER_KEY_LENGTH = 50;
    /** MCP Server 展示名称最大字符数。 */
    public static final int MAX_DISPLAY_NAME_LENGTH = 100;
    /** MCP 端点地址最大字符数。 */
    public static final int MAX_ENDPOINT_URL_LENGTH = 500;
    /** MCP Server 搜索关键字最大字符数。 */
    public static final int MAX_SEARCH_KEYWORD_LENGTH = 100;
    /** 命名空间化后模型可见工具名最大字符数。 */
    public static final int MAX_MODEL_TOOL_NAME_LENGTH = 128;
    /** 单个 MCP Server 最多暴露的工具数量。 */
    public static final int MAX_DISCOVERED_TOOLS = 100;
    /** 单个工具描述最大字符数。 */
    public static final int MAX_TOOL_DESCRIPTION_LENGTH = 4_000;
    /** 单个工具输入 Schema 最大 UTF-8 字节数。 */
    public static final int MAX_TOOL_SCHEMA_BYTES = 64 * 1024;
    /** 单次工具结果最大 UTF-8 字节数。 */
    public static final int MAX_TOOL_RESULT_BYTES = 1024 * 1024;
    /** MCP 认证令牌最大字符数。 */
    public static final int MAX_AUTH_TOKEN_LENGTH = 4_096;
    /** TCP 端口最大值。 */
    public static final int MAX_TCP_PORT = 65_535;
    /** 外部 MCP DNS 解析超时秒数。 */
    public static final int DNS_RESOLUTION_TIMEOUT_SECONDS = 3;
    /** MCP Server 初始配置版本。 */
    public static final long INITIAL_CONFIG_VERSION = 1L;
    /** MCP Server 配置版本递增步长。 */
    public static final long CONFIG_VERSION_INCREMENT = 1L;
    /** 内置 Workbench MCP 来源标识。 */
    public static final String WORKBENCH_SOURCE_KEY = "workbench";
    /** Skill 本地只读工具来源标识。 */
    public static final String SKILL_LOCAL_SOURCE_KEY = "skill-local";

    private McpConstant() {
    }
}
