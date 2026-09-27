package com.agentdoc.evaluation.pojo.vo;

import java.time.LocalDateTime;
import java.util.List;

/** Experiment 状态和编排身份投影。 */
public record ExperimentVO(Long id, Long spaceId, Long datasetVersionId, String status,
                           Integer manifestSchemaVersion, String manifestHash,
                           Long authorizedTokenBudget, Long actualTokenUsage, Long budgetOverrun,
                           String failureCode, String failureMessage,
                           Long createdBy, Long startedBy, LocalDateTime startedAt,
                           Long cancelRequestedBy, LocalDateTime cancelRequestedAt,
                           String decision, String decisionReason, Integer decisionReportRevision,
                           Long decidedBy, LocalDateTime decidedAt,
                           LocalDateTime finishedAt, List<ExperimentVariantVO> variants) {
    public ExperimentVO {
        variants = List.copyOf(variants);
    }
}
