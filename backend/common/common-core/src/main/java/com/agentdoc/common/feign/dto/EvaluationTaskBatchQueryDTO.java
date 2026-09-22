package com.agentdoc.common.feign.dto;

import java.util.List;

/** WorkerCapability 约束下的 Evaluation Task 批量查询。 */
public record EvaluationTaskBatchQueryDTO(Long runId, Long spaceId, List<Long> taskIds) {
}
