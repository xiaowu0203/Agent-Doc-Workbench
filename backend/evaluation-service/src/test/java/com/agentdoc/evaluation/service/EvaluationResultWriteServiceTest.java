package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationEvidenceType;
import com.agentdoc.evaluation.enums.EvaluationMetricDirection;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.metric.EvidenceReferenceValue;
import com.agentdoc.evaluation.metric.EvaluatorResultWriteCommand;
import com.agentdoc.evaluation.metric.StandardMetricOutput;
import com.agentdoc.evaluation.metric.StandardMetricValue;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEvidenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationResultWriteServiceTest {

    @Mock private EvaluationResultMapper resultMapper;
    @Mock private EvaluationMetricMapper metricMapper;
    @Mock private EvaluationEvidenceReferenceMapper evidenceMapper;
    @Mock private EvaluationMetricEvidenceMapper metricEvidenceMapper;
    @Mock private EvaluationCaseAttemptMapper attemptMapper;
    @Mock private EvaluationCaseRunMapper caseRunMapper;
    @Mock private EvaluatorVersionMapper evaluatorVersionMapper;

    private EvaluationResultWriteService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationResultWriteService(resultMapper, metricMapper, evidenceMapper,
                metricEvidenceMapper, attemptMapper, caseRunMapper, evaluatorVersionMapper);
        when(attemptMapper.selectById(13L)).thenReturn(attempt());
        when(caseRunMapper.selectById(12L)).thenReturn(caseRun());
        when(evaluatorVersionMapper.selectById(21L)).thenReturn(evaluatorVersion());
    }

    @Test
    void appendsResultMetricEvidenceAndLinksInOneWriteBoundary() {
        var result = service.append(command(List.of(new StandardMetricOutput(
                        StandardMetricValue.bool("evaluation.artifact-contract.valid", true,
                                EvaluationMetricDirection.HIGHER_IS_BETTER, EvaluationMetricSource.EVALUATOR),
                        List.of("artifact-1"))),
                List.of(new EvidenceReferenceValue("artifact-1", EvaluationEvidenceType.EXECUTION_ARTIFACT,
                        "801", "a".repeat(64), "产物结构校验", "{\"sequence\":1}"))));

        assertThat(result.status()).isEqualTo(EvaluationResultStatus.PASSED.name());
        assertThat(result.metricIds()).hasSize(1);
        assertThat(result.evidenceReferenceIds()).hasSize(1);
        verify(resultMapper).insert(any(EvaluationResultEntity.class));
        verify(metricMapper).insert(any(EvaluationMetricEntity.class));
        verify(evidenceMapper).insert(any(EvaluationEvidenceReferenceEntity.class));
        verify(metricEvidenceMapper).insert(any(EvaluationMetricEvidenceEntity.class));
    }

    @Test
    void rejectsMissingEvidenceReferenceBeforePersistingPartialResult() {
        EvaluatorResultWriteCommand command = command(List.of(new StandardMetricOutput(
                StandardMetricValue.bool("evaluation.artifact-contract.valid", true,
                        EvaluationMetricDirection.HIGHER_IS_BETTER, EvaluationMetricSource.EVALUATOR),
                List.of("missing"))), List.of());

        assertThatThrownBy(() -> service.append(command)).isInstanceOf(RuntimeException.class);

        verify(resultMapper, never()).insert(any(EvaluationResultEntity.class));
        verify(metricMapper, never()).insert(any(EvaluationMetricEntity.class));
    }

    private EvaluatorResultWriteCommand command(List<StandardMetricOutput> metrics,
                                                List<EvidenceReferenceValue> evidence) {
        LocalDateTime started = LocalDateTime.now().minusSeconds(1);
        return new EvaluatorResultWriteCommand(9L, 11L, 12L, 13L, 14L, 21L, 1,
                EvaluationResultStatus.PASSED, "VALID", "{\"diagnostic\":true}",
                "a".repeat(32), "b".repeat(16), started, started.plusSeconds(1), metrics, evidence);
    }

    private EvaluationCaseAttemptEntity attempt() {
        EvaluationCaseAttemptEntity entity = new EvaluationCaseAttemptEntity();
        entity.setId(13L);
        entity.setCaseRunId(12L);
        entity.setRunId(11L);
        entity.setSpaceId(9L);
        entity.setStatus(EvaluationAttemptStatus.EVALUATING.name());
        return entity;
    }

    private EvaluationCaseRunEntity caseRun() {
        EvaluationCaseRunEntity entity = new EvaluationCaseRunEntity();
        entity.setId(12L);
        entity.setRunId(11L);
        entity.setSpaceId(9L);
        entity.setTestCaseVersionId(14L);
        return entity;
    }

    private EvaluatorVersionEntity evaluatorVersion() {
        EvaluatorVersionEntity entity = new EvaluatorVersionEntity();
        entity.setId(21L);
        entity.setSpaceId(9L);
        entity.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        entity.setEvaluatorKey("artifact-contract");
        entity.setImplementationVersion("builtin-1");
        return entity;
    }
}
