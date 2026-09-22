package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "评估运行")
public record EvaluationRunVO(
        Long id,
        Long spaceId,
        Long datasetVersionId,
        Long singleTestCaseVersionId,
        String status,
        String pauseReason,
        Boolean cancelRequested,
        Integer caseCount,
        Integer reconciliationFailureCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<EvaluationCaseRunVO> cases) {

    public static EvaluationRunVO from(EvaluationRunEntity run, List<EvaluationCaseRunVO> cases) {
        return new EvaluationRunVO(run.getId(), run.getSpaceId(), run.getDatasetVersionId(),
                run.getSingleTestCaseVersionId(), run.getStatus(), run.getPauseReason(),
                run.getCancelRequested(), run.getCaseCount(), run.getReconciliationFailureCount(),
                run.getStartedAt(), run.getFinishedAt(),
                List.copyOf(cases));
    }
}
