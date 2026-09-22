package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "评估用例运行")
public record EvaluationCaseRunVO(
        Long id,
        Long testCaseVersionId,
        String status,
        Long currentAttemptId,
        Long replayTaskId,
        Integer attemptNo,
        List<EvaluationFeedbackVO> feedback) {

    public static EvaluationCaseRunVO from(EvaluationCaseRunEntity caseRun,
                                           EvaluationCaseAttemptEntity attempt) {
        return new EvaluationCaseRunVO(caseRun.getId(), caseRun.getTestCaseVersionId(), caseRun.getStatus(),
                caseRun.getCurrentAttemptId(), attempt.getReplayTaskId(), attempt.getAttemptNo(), List.of());
    }

    public static EvaluationCaseRunVO from(EvaluationCaseRunEntity caseRun,
                                           EvaluationCaseAttemptEntity attempt,
                                           List<EvaluationFeedbackVO> feedback) {
        return new EvaluationCaseRunVO(caseRun.getId(), caseRun.getTestCaseVersionId(), caseRun.getStatus(),
                caseRun.getCurrentAttemptId(), attempt.getReplayTaskId(), attempt.getAttemptNo(),
                List.copyOf(feedback));
    }
}
