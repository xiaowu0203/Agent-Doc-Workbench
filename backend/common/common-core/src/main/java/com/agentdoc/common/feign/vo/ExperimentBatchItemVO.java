package com.agentdoc.common.feign.vo;

/** Experiment 批量创建的单项结果。 */
public record ExperimentBatchItemVO(
        Long sourceTaskId,
        Long testCaseVersionId,
        Integer attemptNo,
        String derivationRequestKey,
        Long executionTaskId,
        String status) {
}
