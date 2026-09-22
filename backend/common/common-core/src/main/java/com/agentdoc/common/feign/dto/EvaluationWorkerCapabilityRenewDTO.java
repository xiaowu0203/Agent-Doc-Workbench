package com.agentdoc.common.feign.dto;

import java.util.List;

/** 用户显式操作时，为既有 Evaluation Replay Task 重新签发 WorkerCapability。 */
public record EvaluationWorkerCapabilityRenewDTO(
        Long runId,
        Long spaceId,
        Long ttlSeconds,
        List<Long> taskIds) {
}
