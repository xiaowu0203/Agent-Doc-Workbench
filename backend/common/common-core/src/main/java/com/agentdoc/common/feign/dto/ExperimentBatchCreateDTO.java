package com.agentdoc.common.feign.dto;

import java.util.List;

/** 批量创建隔离 Experiment Task 的内部请求。 */
public record ExperimentBatchCreateDTO(
        Long runId,
        Long variantId,
        Long spaceId,
        Long candidateConfigId,
        Integer candidateSnapshotSchemaVersion,
        String candidateSnapshotHash,
        Long workerCapabilityTtlSeconds,
        List<ExperimentBatchItemDTO> items) {
}
