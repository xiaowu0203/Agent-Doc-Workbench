package com.agentdoc.common.feign.vo;

/** 批量 Replay 的稳定请求映射。 */
public record ReplayBatchItemVO(
        Long sourceTaskId,
        String derivationRequestKey,
        Long replayTaskId,
        String taskStatus) {
}
