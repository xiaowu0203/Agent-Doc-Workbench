package com.agentdoc.common.feign.dto;

import java.time.LocalDateTime;

/**
 * Agent 工具调用用量聚合查询条件。
 *
 * @param spaceId 空间 ID
 * @param startAt 开始时间（含）
 * @param endAt 结束时间（不含）
 * @param agentId Agent ID，可选
 * @param modelId 模型 ID，可选
 * @param executionStatus Agent 执行状态，可选
 */
public record AgentToolUsageQueryDTO(
        Long spaceId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Long agentId,
        Long modelId,
        String executionStatus) {
}
