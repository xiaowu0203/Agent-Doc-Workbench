package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.common.pojo.TokenValue;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Token 本地估算器。未返回的输入/输出 Token 使用 UTF-8 字节数的启发式估算，并明确标记为 ESTIMATED。
 */
@Component
public class TokenUsageEstimator {

    public TokenUsage complete(TokenUsage usage, List<Message> messages,
                               List<ToolCallback> toolCallbacks, ChatResponse response) {
        TokenValue input = usage.input().available()
                ? usage.input() : TokenValue.estimated(estimateInput(messages, toolCallbacks));
        TokenValue output = usage.output().available()
                ? usage.output() : TokenValue.estimated(estimateOutput(response));
        return new TokenUsage(input, usage.cachedInput(), output);
    }

    private long estimateInput(List<Message> messages, List<ToolCallback> toolCallbacks) {
        String messageText = messages.stream()
                .map(Message::toString)
                .collect(Collectors.joining("\n"));
        String toolText = toolCallbacks.stream()
                .map(callback -> callback.getToolDefinition().name()
                        + callback.getToolDefinition().description()
                        + callback.getToolDefinition().inputSchema())
                .collect(Collectors.joining("\n"));
        return estimate(messageText + toolText);
    }

    private long estimateOutput(ChatResponse response) {
        AssistantMessage output = response.getResult().getOutput();
        String toolCalls = output.getToolCalls().stream()
                .map(call -> call.name() + call.arguments())
                .collect(Collectors.joining("\n"));
        return estimate((output.getText() == null ? "" : output.getText()) + toolCalls);
    }

    private long estimate(String value) {
        if (value.isBlank()) {
            return 0L;
        }
        return Math.max(1L, (long) Math.ceil(value.getBytes(StandardCharsets.UTF_8).length / 4.0));
    }
}
