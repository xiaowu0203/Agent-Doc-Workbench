package com.agentdoc.common.feign.dto;

import java.util.List;

/**
 * EvaluationRun 在当前用户上下文中批量创建 Replay 的请求。
 *
 * @param runId EvaluationRun ID
 * @param spaceId 空间 ID
 * @param workerCapabilityTtlSeconds WorkerCapability 有效期秒数
 * @param items Replay 创建项
 */
public record ReplayBatchCreateDTO(
        Long runId,
        Long spaceId,
        Long workerCapabilityTtlSeconds,
        List<ReplayBatchItemDTO> items) {
}
