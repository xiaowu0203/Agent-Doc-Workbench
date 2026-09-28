package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationResultQueryServiceTest {
    @Mock private EvaluationResultMapper resultMapper;
    @Mock private EvaluationMetricMapper metricMapper;
    @Mock private EvaluationMetricEvidenceMapper metricEvidenceMapper;
    @Mock private EvaluationEvidenceReferenceMapper evidenceMapper;
    @Mock private EvaluationCaseAttemptMapper attemptMapper;
    @Mock private EvaluationFeedbackMapper feedbackMapper;
    @Mock private SpaceAccessService spaceAccessService;

    private EvaluationResultQueryService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationResultQueryService(resultMapper, metricMapper, metricEvidenceMapper,
                evidenceMapper, attemptMapper, feedbackMapper, spaceAccessService);
    }

    @Test
    void aggregatesFeedbackWithoutMixingItIntoMetrics() {
        EvaluationResultEntity result = new EvaluationResultEntity();
        result.setId(11L); result.setSpaceId(9L); result.setRunId(21L); result.setCaseAttemptId(31L);
        EvaluationCaseAttemptEntity attempt = new EvaluationCaseAttemptEntity();
        attempt.setId(31L); attempt.setCaseRunId(41L); attempt.setReplayTaskId(51L);
        attempt.setExecutionTaskId(51L);
        EvaluationFeedbackEntity feedback = new EvaluationFeedbackEntity();
        feedback.setId(61L); feedback.setSpaceId(9L); feedback.setCaseRunId(41L); feedback.setLabel("ACCEPTED");
        when(resultMapper.selectById(11L)).thenReturn(result);
        when(metricMapper.selectList(any())).thenReturn(List.of());
        when(evidenceMapper.selectList(any())).thenReturn(List.of());
        when(attemptMapper.selectById(31L)).thenReturn(attempt);
        when(feedbackMapper.selectList(any())).thenReturn(List.of(feedback));

        var detail = service.detail(11L);

        assertThat(detail.metrics()).isEmpty();
        assertThat(detail.feedback()).extracting(value -> value.label()).containsExactly("ACCEPTED");
    }
}
