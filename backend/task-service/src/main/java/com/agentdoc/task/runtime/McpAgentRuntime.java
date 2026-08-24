package com.agentdoc.task.runtime;

import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.pojo.entity.AgentEntity;
import com.agentdoc.task.security.McpConfigCryptoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 真实外部 MCP Runtime。Workbench 主动通过 SSE MCP Client 调用 Agent 工具。
 *
 * <p>mcpConfig 解密后的配置格式：</p>
 * <pre>
 * {
 *   "transport": "sse",
 *   "baseUrl": "https://mcp.example.com",
 *   "sseEndpoint": "/sse",
 *   "toolName": "agent.execute",
 *   "bearerToken": "...",
 *   "headers": {"X-Tenant": "..."},
 *   "requestTimeoutSeconds": 60
 * }
 * </pre>
 */
@Component
public class McpAgentRuntime implements AgentRuntime {
    /** MCP客户端标识，上报给远端MCP Server */
    private static final String CLIENT_NAME = "agent-doc-workbench";
    /** MCP客户端版本号 */
    private static final String CLIENT_VERSION = "0.1.0";

    private final ObjectMapper objectMapper;
    private final McpConfigCryptoService cryptoService;
    private final McpAgentResponseParser responseParser;

    public McpAgentRuntime(ObjectMapper objectMapper, McpConfigCryptoService cryptoService) {
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
        this.responseParser = new McpAgentResponseParser(objectMapper);
    }

    /**
     * Agent执行入口，连接外部MCP‑Server调用指定工具，返回标准化执行结果
     * @param agent Agent数据库实体，持有加密后的mcpConfig与工具白名单
     * @param context Agent执行上下文，携带taskId、文档片段、指令等业务参数
     * @return 转换后内部Agent执行结果
     */
    @Override
    public AgentExecutionResult execute(AgentEntity agent, AgentExecutionContext context) {
        // 解密数据库加密存储的MCP配置JSON，解析为连接配置对象
        McpConnectionConfig config = McpConnectionConfig.parse(cryptoService.decrypt(agent.getMcpConfig()), objectMapper);
        // 安全校验：工具必须在Agent白名单内
        ensureToolAllowed(agent, config.toolName());

        // 构建SSE传输builder，填入基础地址、sse端点、超时时间
        HttpClientSseClientTransport.Builder transportBuilder = HttpClientSseClientTransport
                .builder(config.baseUrl())
                .sseEndpoint(config.sseEndpoint())
                .connectTimeout(config.requestTimeout());
        // 装配鉴权Header（Bearer Token + 用户自定义headers）
        applyHeaders(transportBuilder, config);

        // try‑with‑resources：执行结束自动关闭MCP客户端，释放SSE长连接
        try (McpSyncClient client = McpClient.sync(transportBuilder.build())
                .requestTimeout(config.requestTimeout())
                .initializationTimeout(config.requestTimeout())
                .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
                .build()) {
            // MCP协议握手初始化
            client.initialize();
            // 远程发现校验：MCP‑Server确实提供该toolName工具
            ensureRemoteToolExists(client, config.toolName());
            // 调用远端MCP工具，将MCP返回报文解析为内部AgentExecutionResult
            return responseParser.parse(client.callTool(new McpSchema.CallToolRequest(
                    config.toolName(), buildArguments(context))), context);
        }
    }

    /**
     * 给SSE传输装配HTTP请求头：Bearer鉴权Token + 用户自定义headers
     * <p>注意：如果已经配置bearerToken，避免headers重复覆盖Authorization头。</p>
     * @param builder SSE传输构建器
     * @param config MCP连接配置
     */
    private void applyHeaders(HttpClientSseClientTransport.Builder builder, McpConnectionConfig config) {
        builder.customizeRequest(request -> {
            // Bearer Token鉴权头优先
            if (config.bearerToken() != null && !config.bearerToken().isBlank()) {
                request.header("Authorization", "Bearer " + config.bearerToken());
            }
            // 遍历自定义headers：避免重复设置Authorization（bearerToken优先）
            config.headers().forEach((name, value) -> {
                if (!"Authorization".equalsIgnoreCase(name) || config.bearerToken() == null
                        || config.bearerToken().isBlank()) {
                    request.header(name, value);
                }
            });
        });
    }

    /**
     * 将业务上下文组装成MCP callTool调用参数arguments
     * @param context Agent执行上下文
     * @return MCP工具调用参数字典，约定输出格式responseFormat:agent‑doc‑workbench.v1
     */
    private Map<String, Object> buildArguments(AgentExecutionContext context) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("taskId", context.taskId());
        arguments.put("agentId", context.agentId());
        arguments.put("documentId", context.documentId());
        arguments.put("instruction", context.instruction());
        arguments.put("documentFragment", context.documentFragment());
        arguments.put("fragmentStart", context.fragmentStart());
        arguments.put("documentLength", context.documentLength());
        arguments.put("responseFormat", "agent-doc-workbench.v1");
        return arguments;
    }

    /**
     * 安全校验：待调用工具必须在Agent工具白名单内
     * @param agent Agent实体，toolWhitelist为逗号分隔工具名字符串
     * @param toolName 待调用MCP工具名称
     * @throws IllegalStateException 不在白名单抛出异常，阻断任务执行
     */
    private void ensureToolAllowed(AgentEntity agent, String toolName) {
        List<String> whitelist = splitWhitelist(agent.getToolWhitelist());
        if (whitelist.isEmpty() || !whitelist.contains(toolName)) {
            throw new IllegalStateException("MCP 工具不在 Agent 白名单内: " + toolName);
        }
    }

    /**
     * 调用MCP listTools接口，校验远端MCP‑Server确实存在目标工具
     * @param client MCP同步客户端
     * @param toolName 目标工具名
     * @throws IllegalStateException 远端不存在该工具抛出异常
     */
    private void ensureRemoteToolExists(McpSyncClient client, String toolName) {
        McpSchema.ListToolsResult tools = client.listTools();
        boolean exists = tools != null && tools.tools() != null
                && tools.tools().stream().anyMatch(tool -> toolName.equals(tool.name()));
        if (!exists) {
            throw new IllegalStateException("外部 MCP Server 未提供配置的工具: " + toolName);
        }
    }

    /**
     * 将数据库存储的逗号分隔工具白名单字符串切分为工具名列表，自动trim，过滤空项
     * @param whitelist 原始逗号分隔字符串，允许null/blank
     * @return 工具名称列表；输入为空返回空集合
     */
    private List<String> splitWhitelist(String whitelist) {
        if (whitelist == null || whitelist.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : whitelist.split(",")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    /**
     * MCP连接配置记录；解析自Agent.mcpConfig加密JSON字段
     * <p>仅支持transport=sse；包含地址、端点、目标工具名、鉴权、自定义头、请求超时。</p>
     * @param baseUrl MCP服务基础地址
     * @param sseEndpoint SSE长连接端点路径
     * @param toolName 需要调用的远端MCP工具名称
     * @param bearerToken 鉴权Bearer token，可为null
     * @param headers 用户自定义http请求头
     * @param requestTimeout 连接、初始化、调用整体超时时间
     */
    static record McpConnectionConfig(String baseUrl, String sseEndpoint, String toolName,
                                      String bearerToken, Map<String, String> headers,
                                      Duration requestTimeout) {

        /**
         * 解密后的MCP配置JSON解析为McpConnectionConfig
         * @param json 解密后的原始JSON字符串
         * @param objectMapper jackson序列化器
         * @return 解析后的连接配置记录
         * @throws IllegalStateException 配置缺失字段、传输类型非sse、JSON格式异常抛出
         */
        static McpConnectionConfig parse(String json, ObjectMapper objectMapper) {
            if (json == null || json.isBlank()) {
                throw new IllegalStateException("Agent 未配置 MCP 连接信息");
            }
            try {
                JsonNode root = objectMapper.readTree(json);
                String transport = text(root, "transport", "sse").toLowerCase(Locale.ROOT);
                if (!"sse".equals(transport)) {
                    throw new IllegalStateException("当前仅支持 SSE MCP 传输: " + transport);
                }
                String baseUrl = required(root, "baseUrl");
                String sseEndpoint = text(root, "sseEndpoint", "/sse");
                String toolName = required(root, "toolName");
                long timeoutSeconds = root.path("requestTimeoutSeconds")
                        .asLong(TaskConstant.DEFAULT_MCP_TIMEOUT_SECONDS);
                if (timeoutSeconds < TaskConstant.MIN_MCP_TIMEOUT_SECONDS
                        || timeoutSeconds > TaskConstant.MAX_MCP_TIMEOUT_SECONDS) {
                    throw new IllegalStateException("requestTimeoutSeconds 超出允许范围");
                }
                Map<String, String> headers = new LinkedHashMap<>();
                JsonNode headerNode = root.path("headers");
                if (headerNode.isObject()) {
                    headerNode.fields().forEachRemaining(entry -> headers.put(entry.getKey(), entry.getValue().asText()));
                }
                return new McpConnectionConfig(baseUrl, sseEndpoint, toolName,
                        text(root, "bearerToken", null), headers, Duration.ofSeconds(timeoutSeconds));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("MCP 连接配置不是有效 JSON", e);
            }
        }

        /**
         * 获取必填字段；缺失/空白直接抛异常
         * @param root json根节点
         * @param field 字段名
         * @return 字段文本值
         */
        private static String required(JsonNode root, String field) {
            String value = text(root, field, null);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("MCP 连接配置缺少 " + field);
            }
            return value;
        }

        /**
         * 安全读取json文本节点，null节点返回fallback默认值
         * @param root json节点
         * @param field 字段名
         * @param fallback 兜底值
         * @return 字段文本或fallback
         */
        private static String text(JsonNode root, String field, String fallback) {
            JsonNode value = root == null ? null : root.get(field);
            return value == null || value.isNull() ? fallback : value.asText();
        }
    }
}
