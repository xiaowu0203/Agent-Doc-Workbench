package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationRunStatePolicyTest {
    @Test
    void completedAttemptsProduceCompleted() {
        assertThat(EvaluationRunStatePolicy.aggregate(
                List.of(EvaluationAttemptStatus.COMPLETED), false, false))
                .isEqualTo(EvaluationRunStatus.COMPLETED);
    }

    @Test
    void infrastructureFailureProducesCompletedWithErrors() {
        assertThat(EvaluationRunStatePolicy.aggregate(
                List.of(EvaluationAttemptStatus.COMPLETED, EvaluationAttemptStatus.EVALUATOR_FAILED), false, false))
                .isEqualTo(EvaluationRunStatus.COMPLETED_WITH_ERRORS);
    }

    @Test
    void pauseAndCancelHavePriority() {
        assertThat(EvaluationRunStatePolicy.aggregate(
                List.of(EvaluationAttemptStatus.REPLAY_RUNNING), true, true))
                .isEqualTo(EvaluationRunStatus.PAUSED);
        assertThat(EvaluationRunStatePolicy.aggregate(
                List.of(EvaluationAttemptStatus.CANCELED), false, true))
                .isEqualTo(EvaluationRunStatus.CANCELED);
    }
}
