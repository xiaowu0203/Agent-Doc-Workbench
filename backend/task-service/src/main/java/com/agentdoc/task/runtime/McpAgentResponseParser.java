package com.agentdoc.task.runtime;

import com.agentdoc.common.enums.ChangeOp;
import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.task.constant.TaskConstant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * 将外部 MCP 工具结果转换为任务服务内部的结构化执行结果。
 */
public class McpAgentResponseParser {

    private final ObjectMapper objectMapper;

    public McpAgentResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentExecutionResult parse(McpSchema.CallToolResult result, AgentExecutionContext context) {
        if (result == null) {
            throw new IllegalStateException("MCP 工具未返回结果");
        }
        if (Boolean.TRUE.equals(result.isError())) {
            throw new IllegalStateException("MCP 工具执行失败: " + textContent(result));
        }

        JsonNode payload = structuredPayload(result);
        String fallbackSummary = textContent(result);
        if (payload == null || payload.isNull()) {
            return new AgentExecutionResult(fallbackSummary, List.of(),
                    estimateInputTokens(context), 0, estimateOutputTokens(fallbackSummary));
        }

        String summary = textValue(payload, "summary", fallbackSummary);
        List<ChangeItemDTO> changes = parseChanges(payload.path("changes"));
        long inputTokens = longValue(payload, "inputTokens", estimateInputTokens(context));
        long cachedInputTokens = longValue(payload, "cachedInputTokens", 0);
        long outputTokens = longValue(payload, "outputTokens", estimateOutputTokens(summary));
        return new AgentExecutionResult(summary, changes, inputTokens, cachedInputTokens, outputTokens);
    }

    private JsonNode structuredPayload(McpSchema.CallToolResult result) {
        if (result.structuredContent() != null) {
            return objectMapper.valueToTree(result.structuredContent());
        }
        String text = textContent(result);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(text);
            return parsed != null && parsed.isObject() ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<ChangeItemDTO> parseChanges(JsonNode changesNode) {
        if (!changesNode.isArray()) {
            return List.of();
        }
        List<ChangeItemDTO> changes = new ArrayList<>();
        for (JsonNode changeNode : changesNode) {
            String opCode = textValue(changeNode, "op", null);
            ChangeOp op = ChangeOp.fromCode(opCode);
            String newText = textValue(changeNode, "newText", null);
            if (op == null || newText == null) {
                throw new IllegalStateException("MCP 工具返回了无效的变更项");
            }
            changes.add(new ChangeItemDTO(op, textValue(changeNode, "oldText", null), newText));
        }
        return changes;
    }

    private String textContent(McpSchema.CallToolResult result) {
        if (result.content() == null) {
            return "";
        }
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse("");
    }

    private String textValue(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? Math.max(0, value.asLong()) : fallback;
    }

    private long estimateInputTokens(AgentExecutionContext context) {
        return Math.max(TaskConstant.MIN_ESTIMATED_TOKENS,
                (context.instruction().length() + context.documentFragment().length())
                / TaskConstant.ESTIMATED_CHARACTERS_PER_TOKEN);
    }

    private long estimateOutputTokens(String summary) {
        return Math.max(TaskConstant.MIN_ESTIMATED_TOKENS, summary == null ? 0
                : summary.length() / TaskConstant.ESTIMATED_CHARACTERS_PER_TOKEN);
    }
}
