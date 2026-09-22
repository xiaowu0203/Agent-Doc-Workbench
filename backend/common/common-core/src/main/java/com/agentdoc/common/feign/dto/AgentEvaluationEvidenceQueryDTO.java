package com.agentdoc.common.feign.dto;

import java.util.List;

/** Agent 执行评估事实的批量查询条件。 */
public record AgentEvaluationEvidenceQueryDTO(List<Long> taskIds) {
}
