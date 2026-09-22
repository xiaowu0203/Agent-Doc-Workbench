package com.agentdoc.common.feign.vo;

import java.time.Instant;

/**
 * 既有 Evaluation Replay Task 的窄权限 WorkerCapability 签发结果。
 * <p>
 * 返回给调用方的评估Worker令牌响应，包含令牌本身、绑定上下文以及过期时刻。
 * Worker 使用该JWT调用评估相关接口，权限被限定在 runId、spaceId、taskIdsHash 范围内。
 *
 * @param runId             评估运行实例ID
 * @param spaceId           工作空间ID，权限隔离边界
 * @param taskIdsHash       评估任务ID集合哈希，用于服务端校验任务范围未被篡改
 * @param workerCapability  签发后的Worker窄权限JWT令牌
 * @param expiresAt         令牌过期时间戳（Instant），过期后不可使用
 */
public record EvaluationWorkerCapabilityVO(
        Long runId,
        Long spaceId,
        String taskIdsHash,
        String workerCapability,
        Instant expiresAt) {
}
