package com.agentdoc.agent.execution.model;

import org.springframework.ai.chat.model.ChatResponse;

/**
 * 单轮模型调用结果，保留通用 ChatResponse 供上层工具循环处理 tool_call。
 * <p>
 * 封装一次LLM调用的完整返回数据，由 {@link ModelAdapter#callOnce} / 流式结束后组装返回。
 * </p>
 * @param response SpringAI原始ChatResponse，保留完整原始响应对象，用于解析toolCall、元数据，上层Agent工具循环依赖此字段
 * @param text 模型本轮输出拼接后的纯文本内容，方便直接取文本使用
 * @param tokenUsage 本轮调用Token消耗统计（输入、缓存、输出token）
 */
public record ModelTurnResult(
        ChatResponse response,
        String text,
        TokenUsage tokenUsage) {
}
