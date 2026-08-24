package com.agentdoc.task.runtime;

import com.agentdoc.common.feign.dto.ChangeItemDTO;

import java.util.List;

/**
 * Runtime 统一输出：结构化变更、摘要和本次调用 Token 用量。
 */
public record AgentExecutionResult(String summary, List<ChangeItemDTO> changes,
                                   long inputTokens, long cachedInputTokens, long outputTokens) {

    public long totalTokens() {
        return Math.max(0, inputTokens) + Math.max(0, outputTokens);
    }
}
