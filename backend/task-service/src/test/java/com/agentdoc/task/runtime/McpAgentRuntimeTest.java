package com.agentdoc.task.runtime;

import com.agentdoc.common.enums.ChangeOp;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpAgentRuntimeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentExecutionContext context = new AgentExecutionContext(
            1L, 2L, 3L, "补充摘要", "原文片段", 10L, 100L);

    @Test
    void parsesStructuredMcpResult() {
        Map<String, Object> payload = Map.of(
                "summary", "已补充摘要",
                "changes", List.of(Map.of("op", "replace", "oldText", "旧摘要", "newText", "新摘要")),
                "inputTokens", 12,
                "cachedInputTokens", 3,
                "outputTokens", 8);
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("结果已生成")), false, payload, Map.of());

        AgentExecutionResult parsed = new McpAgentResponseParser(objectMapper).parse(result, context);

        assertEquals("已补充摘要", parsed.summary());
        assertEquals(1, parsed.changes().size());
        assertEquals(ChangeOp.REPLACE, parsed.changes().getFirst().op());
        assertEquals(12, parsed.inputTokens());
        assertEquals(3, parsed.cachedInputTokens());
        assertEquals(8, parsed.outputTokens());
    }

    @Test
    void parsesJsonTextResult() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
                "{\"summary\":\"追加完成\",\"changes\":[{\"op\":\"append\",\"newText\":\"\\n内容\"}]}", false);

        AgentExecutionResult parsed = new McpAgentResponseParser(objectMapper).parse(result, context);

        assertEquals("追加完成", parsed.summary());
        assertEquals(ChangeOp.APPEND, parsed.changes().getFirst().op());
        assertEquals("\n内容", parsed.changes().getFirst().newText());
    }

    @Test
    void keepsFallbackTokenFieldsInCorrectColumns() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult("仅返回摘要", false);

        AgentExecutionResult parsed = new McpAgentResponseParser(objectMapper).parse(result, context);

        assertEquals(0, parsed.cachedInputTokens());
        assertEquals(1, parsed.outputTokens());
    }

    @Test
    void rejectsMcpErrorResult() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult("外部工具失败", true);

        assertThrows(IllegalStateException.class,
                () -> new McpAgentResponseParser(objectMapper).parse(result, context));
    }

    @Test
    void validatesSseConnectionConfig() {
        McpAgentRuntime.McpConnectionConfig config = McpAgentRuntime.McpConnectionConfig.parse("""
                {
                  "transport": "sse",
                  "baseUrl": "https://mcp.example.com",
                  "sseEndpoint": "/events",
                  "toolName": "agent.execute",
                  "bearerToken": "secret",
                  "headers": {"X-Tenant": "demo"},
                  "requestTimeoutSeconds": 30
                }
                """, objectMapper);

        assertEquals("https://mcp.example.com", config.baseUrl());
        assertEquals("/events", config.sseEndpoint());
        assertEquals("agent.execute", config.toolName());
        assertEquals("demo", config.headers().get("X-Tenant"));
        assertEquals(30, config.requestTimeout().toSeconds());
    }

    @Test
    void rejectsUnsupportedTransport() {
        assertThrows(IllegalStateException.class, () -> McpAgentRuntime.McpConnectionConfig.parse("""
                {"transport":"stdio","baseUrl":"local","toolName":"agent.execute"}
                """, objectMapper));
    }
}
