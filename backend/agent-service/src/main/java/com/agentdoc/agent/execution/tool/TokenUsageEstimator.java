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
 * Token用量补齐与估算组件
 * <p>
 * 场景：部分模型接口不返回真实token usage统计字段。
 * 优先使用接口返回的原始TokenValue；当接口未返回时，基于文本UTF‑8字节做简易估算，标记为estimated（估算值）。
 * <b>估算仅用于预算熔断、统计参考，不是精确值。</b>
 * 估算公式：UTF‑8字节长度 / 4，近似大模型token平均字节占比。
 * </p>
 */
@Component
public class TokenUsageEstimator {

    /**
     * 补齐TokenUsage对象，输入、输出任一缺失时执行文本估算
     *
     * @param usage       模型接口返回的原始token用量，可能部分字段不可用
     * @param messages    本轮输入全部消息列表，用于input估算
     * @param toolCallbacks 可用工具列表（工具名称、描述、入参schema），计入输入token估算
     * @param response    LLM原始响应对象，用于output估算
     * @return 补齐后的TokenUsage，缺失字段替换为估算TokenValue，保留cachedInput原值
     */
    public TokenUsage complete(TokenUsage usage, List<Message> messages,
                               List<ToolCallback> toolCallbacks, ChatResponse response) {
        TokenValue input = usage.input().available()
                ? usage.input() : TokenValue.estimated(estimateInput(messages, toolCallbacks));
        TokenValue output = usage.output().available()
                ? usage.output() : TokenValue.estimated(estimateOutput(response));
        return new TokenUsage(input, usage.cachedInput(), output);
    }

    /**
     * 估算输入侧token：会话消息 + 全部工具定义文本
     *
     * @param messages      历史/本轮消息集合
     * @param toolCallbacks 工具列表，工具名称、描述、schema全部拼接参与估算
     * @return 估算token数量
     */
    private long estimateInput(List<Message> messages, List<ToolCallback> toolCallbacks) {
        // 拼接全部会话消息文本
        String messageText = messages.stream()
                .map(Message::toString)
                .collect(Collectors.joining("\n"));
        // 拼接全部工具元信息：名称+描述+输入schema
        String toolText = toolCallbacks.stream()
                .map(callback -> callback.getToolDefinition().name()
                        + callback.getToolDefinition().description()
                        + callback.getToolDefinition().inputSchema())
                .collect(Collectors.joining("\n"));
        return estimate(messageText + toolText);
    }

    /**
     * 估算输出侧token：模型回复文本 + 工具调用名称与参数
     *
     * @param response LLM完整响应
     * @return 估算token数量
     */
    private long estimateOutput(ChatResponse response) {
        AssistantMessage output = response.getResult().getOutput();
        // 拼接全部工具调用：工具名 + arguments参数json
        String toolCalls = output.getToolCalls().stream()
                .map(call -> call.name() + call.arguments())
                .collect(Collectors.joining("\n"));
        // 模型文本内容 + 工具调用内容合并估算
        return estimate((output.getText() == null ? "" : output.getText()) + toolCalls);
    }

    /**
     * 简易token估算工具
     * <p>规则：UTF‑8字节数 ÷ 4；空文本返回0；结果最小返回1。
     * 仅做预算熔断参考，不等于模型真实分词数量。</p>
     *
     * @param value 待估算原始文本
     * @return 估算token数量
     */
    private long estimate(String value) {
        if (value.isBlank()) {
            return 0L;
        }
        return Math.max(1L, (long) Math.ceil(value.getBytes(StandardCharsets.UTF_8).length / 4.0));
    }
}
