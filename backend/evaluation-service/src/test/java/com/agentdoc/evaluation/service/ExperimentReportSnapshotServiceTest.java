package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentVariantEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentReportSnapshotServiceTest {

    @Mock private EvaluationRunMapper runMapper;
    @Mock private EvaluationCaseRunMapper caseRunMapper;
    @Mock private EvaluationCaseAttemptMapper attemptMapper;
    @Mock private EvaluationResultMapper resultMapper;
    @Mock private EvaluationMetricMapper metricMapper;
    @Mock private EvaluatorVersionMapper evaluatorVersionMapper;
    @InjectMocks private ExperimentReportSnapshotService service;

    @Test
    void selectsOnlyCurrentAttemptMetrics() {
        when(runMapper.selectBatchIds(any())).thenReturn(List.of(run()));
        EvaluationCaseRunEntity caseRun = new EvaluationCaseRunEntity();
        caseRun.setId(20L);
        caseRun.setRunId(10L);
        caseRun.setTestCaseVersionId(100L);
        caseRun.setCurrentAttemptId(31L);
        when(caseRunMapper.selectList(any())).thenReturn(List.of(caseRun));
        EvaluationCaseAttemptEntity attempt = new EvaluationCaseAttemptEntity();
        attempt.setId(31L);
        attempt.setCaseRunId(20L);
        attempt.setRunId(10L);
        attempt.setSpaceId(2L);
        attempt.setStatus("COMPLETED");
        when(attemptMapper.selectBatchIds(any())).thenReturn(List.of(attempt));
        when(resultMapper.selectList(any())).thenReturn(List.of());
        when(metricMapper.selectList(any())).thenReturn(List.of(metric(41L, 30L), metric(42L, 31L)));

        ExperimentReportSnapshotService.Snapshot snapshot = service.load(experiment(), manifest(),
                List.of(variant()));

        assertEquals(List.of(31L), snapshot.attemptIds());
        assertEquals(List.of(42L), snapshot.metricIds());
        assertEquals(42L, snapshot.variants().getFirst().metrics().getFirst().getId());
    }

    @Test
    void absentCaseRemainsMissing() {
        when(runMapper.selectBatchIds(any())).thenReturn(List.of(run()));
        when(caseRunMapper.selectList(any())).thenReturn(List.of());
        when(resultMapper.selectList(any())).thenReturn(List.of());
        when(metricMapper.selectList(any())).thenReturn(List.of());

        ExperimentReportSnapshotService.Snapshot snapshot = service.load(experiment(), manifest(),
                List.of(variant()));

        assertEquals("EVIDENCE_MISSING", snapshot.variants().getFirst().missingReasons().get(100L));
        assertEquals(List.of(), snapshot.attemptIds());
    }

    @Test
    void evaluatorErrorAndVersionDriftAreDistinctMissingReasons() {
        when(runMapper.selectBatchIds(any())).thenReturn(List.of(run()));
        EvaluationCaseRunEntity caseRun = new EvaluationCaseRunEntity();
        caseRun.setId(20L);
        caseRun.setRunId(10L);
        caseRun.setTestCaseVersionId(100L);
        caseRun.setCurrentAttemptId(31L);
        when(caseRunMapper.selectList(any())).thenReturn(List.of(caseRun));
        EvaluationCaseAttemptEntity attempt = new EvaluationCaseAttemptEntity();
        attempt.setId(31L);
        attempt.setCaseRunId(20L);
        attempt.setRunId(10L);
        attempt.setSpaceId(2L);
        attempt.setStatus("EVALUATOR_FAILED");
        when(attemptMapper.selectBatchIds(any())).thenReturn(List.of(attempt));
        EvaluationResultEntity result = new EvaluationResultEntity();
        result.setId(40L);
        result.setCaseAttemptId(31L);
        result.setEvaluatorVersionId(50L);
        result.setEvaluationAttemptNo(1);
        result.setStatus("ERROR");
        when(resultMapper.selectList(any())).thenReturn(List.of(result));
        when(metricMapper.selectList(any())).thenReturn(List.of());
        EvaluatorVersionEntity version = new EvaluatorVersionEntity();
        version.setId(50L);
        version.setSpaceId(2L);
        version.setEvaluatorKey("quality");
        version.setContentHash("frozen");
        when(evaluatorVersionMapper.selectBatchIds(any())).thenReturn(List.of(version));

        ExperimentReportSnapshotService.Snapshot failed = service.load(experiment(), evaluatorManifest(),
                List.of(variant()));
        assertEquals("EVALUATION_FAILED", failed.variants().getFirst().evaluatorIssues()
                .get(new ExperimentReportMatrix.EvaluatorKey(100L, 50L)));

        version.setContentHash("changed");
        ExperimentReportSnapshotService.Snapshot drifted = service.load(experiment(), evaluatorManifest(),
                List.of(variant()));
        assertEquals("EVALUATOR_VERSION_MISMATCH", drifted.variants().getFirst().evaluatorIssues()
                .get(new ExperimentReportMatrix.EvaluatorKey(100L, 50L)));
    }

    private ExperimentEntity experiment() {
        ExperimentEntity entity = new ExperimentEntity();
        entity.setId(1L);
        entity.setSpaceId(2L);
        entity.setDatasetVersionId(3L);
        return entity;
    }

    private ExperimentVariantEntity variant() {
        ExperimentVariantEntity entity = new ExperimentVariantEntity();
        entity.setId(4L);
        entity.setEvaluationRunId(10L);
        entity.setVariantKey("baseline");
        return entity;
    }

    private EvaluationRunEntity run() {
        EvaluationRunEntity entity = new EvaluationRunEntity();
        entity.setId(10L);
        entity.setSpaceId(2L);
        entity.setDatasetVersionId(3L);
        entity.setExperimentVariantId(4L);
        entity.setStatus("COMPLETED");
        return entity;
    }

    private ExperimentManifest manifest() {
        return new ExperimentManifest(3L,
                List.of(new ManifestCase(100L, 101L, 102L, 1, "input", 103L, 0L, "document", List.of())),
                3, "snapshot", 1, List.of(), "catalog");
    }

    private ExperimentManifest evaluatorManifest() {
        return new ExperimentManifest(3L,
                List.of(new ManifestCase(100L, 101L, 102L, 1, "input", 103L, 0L, "document",
                        List.of(new ManifestEvaluator(50L, "quality", "frozen")))),
                3, "snapshot", 1, List.of(), "catalog");
    }

    private EvaluationMetricEntity metric(Long id, Long attemptId) {
        EvaluationMetricEntity entity = new EvaluationMetricEntity();
        entity.setId(id);
        entity.setRunId(10L);
        entity.setCaseRunId(20L);
        entity.setCaseAttemptId(attemptId);
        entity.setSource("EXECUTION");
        return entity;
    }
}
