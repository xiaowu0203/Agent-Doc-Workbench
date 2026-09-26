package com.agentdoc.common.feign.dto;

/** Experiment 批量创建的单项冻结请求。 */
public record ExperimentBatchItemDTO(
        Long sourceTaskId,
        Long testCaseVersionId,
        Integer attemptNo,
        String derivationRequestKey) {
}
