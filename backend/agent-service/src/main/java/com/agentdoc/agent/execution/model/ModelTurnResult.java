package com.agentdoc.agent.execution.model;

import org.springframework.ai.chat.model.ChatResponse;

/** 单轮模型调用结果，保留通用 ChatResponse 供上层工具循环处理 tool_call。 */
public record ModelTurnResult(
        ChatResponse response,
        String text,
        TokenUsage tokenUsage) {
}
