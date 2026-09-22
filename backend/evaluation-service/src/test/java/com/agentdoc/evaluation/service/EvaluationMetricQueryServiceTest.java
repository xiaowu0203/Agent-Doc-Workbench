package com.agentdoc.evaluation.service;

import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.MetricComparisonQueryDTO;
import com.agentdoc.evaluation.enums.EvaluationMetricDirection;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import com.agentdoc.evaluation.enums.EvaluationMetricValueType;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationMetricQueryServiceTest {
    @Mock private EvaluationMetricMapper metricMapper;
    @Mock private EvaluationCaseRunMapper caseRunMapper;
    @Mock private EvaluationCaseAttemptMapper attemptMapper;
    @Mock private EvaluationResultMapper resultMapper;
    @Mock private EvaluationMetricEvidenceMapper metricEvidenceMapper;
    @Mock private EvaluationEvidenceReferenceMapper evidenceMapper;
    @Mock private EvaluationRunMapper runMapper;
    @Mock private SpaceAccessService spaceAccessService;

    private EvaluationMetricQueryService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationMetricQueryService(metricMapper, caseRunMapper, attemptMapper, resultMapper,
                metricEvidenceMapper, evidenceMapper, runMapper, spaceAccessService);
        when(runMapper.selectBatchIds(any())).thenReturn(List.of(run(1L), run(2L)));
        when(metricEvidenceMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void emitsExplicitMissingValueForRunWithoutEffectiveMetric() {
        when(metricMapper.selectList(any())).thenReturn(List.of(metric(101L, 1L, 11L, 21L,
                EvaluationMetricDirection.HIGHER_IS_BETTER)));
        when(caseRunMapper.selectBatchIds(any())).thenReturn(List.of(caseRun(11L, 21L)));

        var result = service.comparisonInput(query());

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().values()).hasSize(2);
        assertThat(result.rows().getFirst().values().get(1).metric()).isNull();
        assertThat(result.rows().getFirst().values().get(1).missingReason()).isEqualTo("FACT_UNAVAILABLE");
    }

    @Test
    void rejectsComparisonWhenPersistedContractsConflict() {
        when(metricMapper.selectList(any())).thenReturn(List.of(
                metric(101L, 1L, 11L, 21L, EvaluationMetricDirection.HIGHER_IS_BETTER),
                metric(102L, 2L, 12L, 22L, EvaluationMetricDirection.LOWER_IS_BETTER)));
        when(caseRunMapper.selectBatchIds(any())).thenReturn(List.of(caseRun(11L, 21L), caseRun(12L, 22L)));

        assertThatThrownBy(() -> service.comparisonInput(query()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("契约不一致");
    }

    @Test
    void exposesLatestEvaluatorErrorAsMissingReasonWithoutFallingBack() {
        EvaluationMetricEntity oldMetric = evaluatorMetric(101L, 1L, 11L, 21L, 201L);
        EvaluationMetricEntity currentMetric = evaluatorMetric(102L, 2L, 12L, 22L, 203L);
        when(metricMapper.selectList(any())).thenReturn(List.of(oldMetric, currentMetric));
        List<EvaluationCaseRunEntity> caseRuns = List.of(
                caseRun(11L, 1L, 21L), caseRun(12L, 2L, 22L));
        when(caseRunMapper.selectBatchIds(any())).thenReturn(caseRuns);
        when(caseRunMapper.selectList(any())).thenReturn(caseRuns);
        when(resultMapper.selectList(any())).thenReturn(List.of(
                result(201L, 21L, 31L, 1, EvaluationResultStatus.PASSED),
                result(202L, 21L, 31L, 2, EvaluationResultStatus.ERROR),
                result(203L, 22L, 31L, 1, EvaluationResultStatus.PASSED)));

        var result = service.comparisonInput(query());

        assertThat(result.rows().getFirst().values().getFirst().metric()).isNull();
        assertThat(result.rows().getFirst().values().getFirst().missingReason())
                .isEqualTo("EVALUATOR_ERROR");
    }

    private static MetricComparisonQueryDTO query() {
        return new MetricComparisonQueryDTO(9L, List.of(1L, 2L), List.of(), List.of());
    }

    private static EvaluationRunEntity run(Long id) {
        EvaluationRunEntity entity = new EvaluationRunEntity();
        entity.setId(id);
        entity.setSpaceId(9L);
        return entity;
    }

    private static EvaluationCaseRunEntity caseRun(Long id, Long attemptId) {
        return caseRun(id, null, attemptId);
    }

    private static EvaluationCaseRunEntity caseRun(Long id, Long runId, Long attemptId) {
        EvaluationCaseRunEntity entity = new EvaluationCaseRunEntity();
        entity.setId(id);
        entity.setRunId(runId);
        entity.setTestCaseVersionId(51L);
        entity.setCurrentAttemptId(attemptId);
        return entity;
    }

    private static EvaluationMetricEntity metric(Long id, Long runId, Long caseRunId, Long attemptId,
                                                  EvaluationMetricDirection direction) {
        EvaluationMetricEntity entity = new EvaluationMetricEntity();
        entity.setId(id);
        entity.setSpaceId(9L);
        entity.setRunId(runId);
        entity.setCaseRunId(caseRunId);
        entity.setCaseAttemptId(attemptId);
        entity.setTestCaseVersionId(51L);
        entity.setContractVersion(1);
        entity.setSource(EvaluationMetricSource.EXECUTION.name());
        entity.setProducerId(attemptId);
        entity.setMetricKey("execution.latency");
        entity.setValueType(EvaluationMetricValueType.NUMBER.name());
        entity.setNumericValue(BigDecimal.ONE);
        entity.setUnit("ms");
        entity.setDirection(direction.name());
        return entity;
    }

    private static EvaluationMetricEntity evaluatorMetric(Long id, Long runId, Long caseRunId,
                                                           Long attemptId, Long resultId) {
        EvaluationMetricEntity entity = metric(id, runId, caseRunId, attemptId,
                EvaluationMetricDirection.HIGHER_IS_BETTER);
        entity.setSource(EvaluationMetricSource.EVALUATOR.name());
        entity.setEvaluationResultId(resultId);
        entity.setEvaluatorVersionId(31L);
        entity.setProducerId(resultId);
        entity.setMetricKey("evaluation.artifact-contract.valid");
        entity.setValueType(EvaluationMetricValueType.BOOLEAN.name());
        entity.setNumericValue(null);
        entity.setBooleanValue(true);
        entity.setUnit("boolean");
        return entity;
    }

    private static EvaluationResultEntity result(Long id, Long attemptId, Long evaluatorVersionId,
                                                 int attemptNo, EvaluationResultStatus status) {
        EvaluationResultEntity entity = new EvaluationResultEntity();
        entity.setId(id);
        entity.setCaseAttemptId(attemptId);
        entity.setEvaluatorVersionId(evaluatorVersionId);
        entity.setEvaluationAttemptNo(attemptNo);
        entity.setStatus(status.name());
        return entity;
    }
}
