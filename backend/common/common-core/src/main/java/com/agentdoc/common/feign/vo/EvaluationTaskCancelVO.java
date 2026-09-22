package com.agentdoc.common.feign.vo;

/** WorkerCapability 批量取消单项结果。 */
public record EvaluationTaskCancelVO(
        Long taskId,
        boolean accepted,
        String status,
        String reasonCode) {
}
