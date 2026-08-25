package com.agentdoc.agent.execution.model;

import com.agentdoc.common.pojo.TokenValue;

/**
 * Token用量统计记录对象
 * <p>
 * 记录一轮模型调用的Token消耗；支持多轮累加，用于Agent多轮对话累计总消耗。
 * 区分普通输入、缓存命中输入、输出三类Token；每一项使用{@link TokenValue}包装，
 * 支持区分：厂商返回真实值 / 本地估算值 / 无法获取。
 * </p>
 * @param input 普通输入Prompt Token（非缓存命中部分）
 * @param cachedInput 缓存命中节省的输入Token，OpenAI/Anthropic/Gemini缓存特性，无缓存则为unavailable
 * @param output 模型生成输出Token
 */
public record TokenUsage(
        TokenValue input,
        TokenValue cachedInput,
        TokenValue output) {

    public static TokenUsage unavailable() {
        return new TokenUsage(TokenValue.unavailable(), TokenValue.unavailable(), TokenValue.unavailable());
    }

    /**
     * 累加两组Token用量，用于Agent多轮模型调用汇总总消耗
     * <p>各个分项分别调用{@link TokenValue#add(TokenValue,TokenValue)}做合并，
     * 来源优先级：PROVIDER真实值 > ESTIMATED估算值 > UNAVAILABLE不可用。</p>
     * @param other 需要被累加的另一轮TokenUsage
     * @return 全新的累加后TokenUsage实例，原对象不会变更
     */
    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(TokenValue.add(input, other.input),
                TokenValue.add(cachedInput, other.cachedInput),
                TokenValue.add(output, other.output));
    }
}
