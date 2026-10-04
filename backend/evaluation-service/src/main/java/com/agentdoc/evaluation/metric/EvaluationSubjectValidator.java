package com.agentdoc.evaluation.metric;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import java.util.Arrays;

/** 应用与 MySQL 5.7 触发器共同执行主体互斥；不以 CHECK 代替。 */
public final class EvaluationSubjectValidator {
    private EvaluationSubjectValidator() { }

    public static void validate(EvaluationResultEntity value) {
        validate(value.getSubjectType(), new Long[]{value.getRunId(), value.getCaseAttemptId()},
                value.getOnlineAssignmentId(), value.getOnlineEvaluationAttemptId(), value.getTaskId(), value.getExecutionId());
    }
    public static void validate(EvaluationMetricEntity value) {
        validate(value.getSubjectType(), new Long[]{value.getRunId(), value.getCaseRunId(), value.getCaseAttemptId(), value.getTestCaseVersionId()},
                value.getOnlineAssignmentId(), value.getOnlineEvaluationAttemptId(), value.getTaskId(), value.getExecutionId());
    }
    public static void validate(EvaluationEvidenceReferenceEntity value) {
        validate(value.getSubjectType(), new Long[]{value.getCaseAttemptId()},
                value.getOnlineAssignmentId(), value.getOnlineEvaluationAttemptId(), value.getTaskId(), value.getExecutionId());
    }
    private static void validate(String subject, Long[] offline, Long... online) {
        boolean valid = "CASE_ATTEMPT".equals(subject) && Arrays.stream(offline).allMatch(v -> v != null && v > 0)
                && Arrays.stream(online).allMatch(v -> v == null)
                || "ONLINE_TASK".equals(subject) && Arrays.stream(offline).allMatch(v -> v == null)
                && Arrays.stream(online).allMatch(v -> v != null && v > 0);
        if (!valid) { throw new BusinessException(ErrorCode.BAD_REQUEST, EVALUATION_SUBJECT_INVALID.name()); }
    }
}
