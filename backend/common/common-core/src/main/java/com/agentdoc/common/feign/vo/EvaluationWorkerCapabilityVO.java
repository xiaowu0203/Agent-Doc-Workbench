package com.agentdoc.common.feign.vo;

import java.time.Instant;

/** 既有 Evaluation Replay Task 的窄权限 WorkerCapability 签发结果。 */
public record EvaluationWorkerCapabilityVO(
        Long runId,
        Long spaceId,
        String taskIdsHash,
        String workerCapability,
        Instant expiresAt) {
}
