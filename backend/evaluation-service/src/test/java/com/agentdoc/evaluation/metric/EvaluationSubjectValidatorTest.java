package com.agentdoc.evaluation.metric;

import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EvaluationSubjectValidatorTest {
    @Test
    void resultRequiresExactlyOneCompleteSubject() {
        var value = new EvaluationResultEntity();
        value.setRunId(1L); value.setCaseAttemptId(2L);
        assertThatCode(() -> EvaluationSubjectValidator.validate(value)).doesNotThrowAnyException();
        value.setOnlineAssignmentId(3L);
        assertThatThrownBy(() -> EvaluationSubjectValidator.validate(value)).hasMessageContaining("EVALUATION_SUBJECT_INVALID");
        value.setSubjectType("ONLINE_TASK"); value.setRunId(null); value.setCaseAttemptId(null);
        value.setOnlineEvaluationAttemptId(4L); value.setTaskId(5L); value.setExecutionId(6L);
        assertThatCode(() -> EvaluationSubjectValidator.validate(value)).doesNotThrowAnyException();
        value.setExecutionId(null);
        assertThatThrownBy(() -> EvaluationSubjectValidator.validate(value)).hasMessageContaining("EVALUATION_SUBJECT_INVALID");
    }

    @Test
    void metricAndEvidenceCannotMixOfflineAndOnlineIdsOrUseZeroIds() {
        var metric = new EvaluationMetricEntity();
        metric.setSubjectType("ONLINE_TASK"); metric.setOnlineAssignmentId(1L); metric.setOnlineEvaluationAttemptId(2L);
        metric.setTaskId(3L); metric.setExecutionId(4L);
        assertThatCode(() -> EvaluationSubjectValidator.validate(metric)).doesNotThrowAnyException();
        metric.setCaseRunId(5L);
        assertThatThrownBy(() -> EvaluationSubjectValidator.validate(metric)).hasMessageContaining("EVALUATION_SUBJECT_INVALID");
        var evidence = new EvaluationEvidenceReferenceEntity();
        evidence.setCaseAttemptId(0L);
        assertThatThrownBy(() -> EvaluationSubjectValidator.validate(evidence)).hasMessageContaining("EVALUATION_SUBJECT_INVALID");
        evidence.setCaseAttemptId(1L);
        assertThatCode(() -> EvaluationSubjectValidator.validate(evidence)).doesNotThrowAnyException();
    }
}
