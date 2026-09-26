package com.agentdoc.common.feign.dto;

/**
 * 批量创建 Replay 的单项请求。
 *
 * @param sourceTaskId 来源任务 ID
 * @param derivationRequestKey 派生请求幂等键
 * @param experimentVariantId Experiment baseline Variant ID；普通 EvaluationRun 为空
 * @param testCaseVersionId Experiment 用例版本 ID；普通 EvaluationRun 为空
 * @param attemptNo Experiment Attempt 序号；普通 EvaluationRun 为空
 */
public record ReplayBatchItemDTO(Long sourceTaskId, String derivationRequestKey,
                                 Long experimentVariantId, Long testCaseVersionId, Integer attemptNo) {
    public ReplayBatchItemDTO(Long sourceTaskId, String derivationRequestKey) {
        this(sourceTaskId, derivationRequestKey, null, null, null);
    }
}
