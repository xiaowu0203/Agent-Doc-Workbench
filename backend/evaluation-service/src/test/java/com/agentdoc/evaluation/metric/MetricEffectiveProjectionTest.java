package com.agentdoc.evaluation.metric;

import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricEffectiveProjectionTest {

    @Test
    void keepsCurrentExecutionMetricAndOnlyLatestEvaluatorResultMetric() {
        EvaluationCaseRunEntity caseRun = caseRun(11L, 102L);
        EvaluationMetricEntity previousAttempt = metric(1L, 11L, 101L,
                EvaluationMetricSource.EXECUTION, null, null);
        EvaluationMetricEntity execution = metric(2L, 11L, 102L,
                EvaluationMetricSource.EXECUTION, null, null);
        EvaluationMetricEntity oldEvaluation = metric(3L, 11L, 102L,
                EvaluationMetricSource.EVALUATOR, 201L, 31L);
        EvaluationMetricEntity latestEvaluation = metric(4L, 11L, 102L,
                EvaluationMetricSource.EVALUATOR, 202L, 31L);

        List<EvaluationMetricEntity> selected = MetricEffectiveProjection.select(
                List.of(previousAttempt, execution, oldEvaluation, latestEvaluation),
                List.of(caseRun), List.of(result(201L, 102L, 31L, 1), result(202L, 102L, 31L, 2)));

        assertThat(selected).extracting(EvaluationMetricEntity::getId).containsExactly(2L, 4L);
    }

    @Test
    void removesOldMetricWhenLatestEvaluatorResultHasNoMetric() {
        EvaluationMetricEntity oldEvaluation = metric(3L, 11L, 102L,
                EvaluationMetricSource.EVALUATOR, 201L, 31L);

        List<EvaluationMetricEntity> selected = MetricEffectiveProjection.select(
                List.of(oldEvaluation), List.of(caseRun(11L, 102L)),
                List.of(result(201L, 102L, 31L, 1), result(202L, 102L, 31L, 2)));

        assertThat(selected).isEmpty();
    }

    private static EvaluationCaseRunEntity caseRun(Long id, Long currentAttemptId) {
        EvaluationCaseRunEntity entity = new EvaluationCaseRunEntity();
        entity.setId(id);
        entity.setCurrentAttemptId(currentAttemptId);
        return entity;
    }

    private static EvaluationMetricEntity metric(Long id, Long caseRunId, Long attemptId,
                                                  EvaluationMetricSource source, Long resultId,
                                                  Long evaluatorVersionId) {
        EvaluationMetricEntity entity = new EvaluationMetricEntity();
        entity.setId(id);
        entity.setCaseRunId(caseRunId);
        entity.setCaseAttemptId(attemptId);
        entity.setSource(source.name());
        entity.setEvaluationResultId(resultId);
        entity.setEvaluatorVersionId(evaluatorVersionId);
        return entity;
    }

    private static EvaluationResultEntity result(Long id, Long attemptId, Long evaluatorVersionId,
                                                 int evaluationAttemptNo) {
        EvaluationResultEntity entity = new EvaluationResultEntity();
        entity.setId(id);
        entity.setCaseAttemptId(attemptId);
        entity.setEvaluatorVersionId(evaluatorVersionId);
        entity.setEvaluationAttemptNo(evaluationAttemptNo);
        entity.setStatus(EvaluationResultStatus.ERROR.name());
        return entity;
    }
}
