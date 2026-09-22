package com.agentdoc.common.feign.dto;

/**
 * 批量创建 Replay 的单项请求。
 *
 * @param sourceTaskId 来源任务 ID
 * @param derivationRequestKey 派生请求幂等键
 */
public record ReplayBatchItemDTO(Long sourceTaskId, String derivationRequestKey) {
}
