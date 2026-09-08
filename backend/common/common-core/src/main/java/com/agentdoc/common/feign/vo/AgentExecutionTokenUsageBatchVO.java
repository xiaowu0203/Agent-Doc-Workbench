package com.agentdoc.common.feign.vo;

/**
 * 按工作台任务关联的 Agent 执行 Token 用量投影。
 *
 * @param taskId 工作台任务 ID
 * @param inputTokens 输入 Token 数
 * @param inputTokensEstimated 输入 Token 是否为估算值
 * @param cachedInputTokens 缓存输入 Token 数
 * @param cachedInputTokensEstimated 缓存输入 Token 是否为估算值
 * @param outputTokens 输出 Token 数
 * @param outputTokensEstimated 输出 Token 是否为估算值
 */
public record AgentExecutionTokenUsageBatchVO(
        Long taskId,
        Long inputTokens,
        Boolean inputTokensEstimated,
        Long cachedInputTokens,
        Boolean cachedInputTokensEstimated,
        Long outputTokens,
        Boolean outputTokensEstimated) {
}
