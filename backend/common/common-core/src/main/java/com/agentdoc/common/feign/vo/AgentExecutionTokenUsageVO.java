package com.agentdoc.common.feign.vo;

/**
 * Agent 执行 Token 用量的内部服务间投影，不包含提示词、工具参数等审计内容。
 */
public record AgentExecutionTokenUsageVO(
        Long inputTokens,
        Boolean inputTokensEstimated,
        Long cachedInputTokens,
        Boolean cachedInputTokensEstimated,
        Long outputTokens,
        Boolean outputTokensEstimated) {
}
