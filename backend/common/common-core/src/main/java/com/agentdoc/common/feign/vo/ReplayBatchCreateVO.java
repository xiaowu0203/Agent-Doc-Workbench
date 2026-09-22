package com.agentdoc.common.feign.vo;

import java.time.Instant;
import java.util.List;

/** 批量 Replay 创建结果以及仅绑定本批 Task 集合的 WorkerCapability。 */
public record ReplayBatchCreateVO(
        Long runId,
        Long spaceId,
        List<ReplayBatchItemVO> items,
        String taskIdsHash,
        String workerCapability,
        Instant workerCapabilityExpiresAt) {
}
