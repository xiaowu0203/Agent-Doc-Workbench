package com.agentdoc.common.feign.vo;

import java.time.Instant;
import java.util.List;

/** 批量创建隔离 Experiment Task 的结果。 */
public record ExperimentBatchCreateVO(
        Long runId,
        Long variantId,
        Long spaceId,
        List<ExperimentBatchItemVO> items,
        String taskIdsHash,
        String workerCapability,
        Instant workerCapabilityExpiresAt) {

    public ExperimentBatchCreateVO {
        items = List.copyOf(items);
    }
}
