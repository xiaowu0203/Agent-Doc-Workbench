package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.vo.AgentCandidateConfigVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.mapper.ExperimentMapper;
import com.agentdoc.evaluation.mapper.ExperimentReportMapper;
import com.agentdoc.evaluation.mapper.ExperimentVariantMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentVariantEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentReportServiceTest {

    @Mock private ExperimentMapper experimentMapper;
    @Mock private ExperimentVariantMapper variantMapper;
    @Mock private EvaluationTestCaseVersionMapper testCaseVersionMapper;
    @Mock private ExperimentReportMapper reportMapper;
    @Mock private ExperimentReportSnapshotService snapshotService;
    @Mock private ExperimentReportEvidenceService evidenceService;
    @Mock private ExperimentReportPersistenceService persistenceService;
    @Mock private SpaceAccessService spaceAccessService;
    @Mock private AgentFeign agentFeign;
    @InjectMocks private ExperimentReportService service;

    @Test
    void terminalExperimentProducesFrozenReportInputAndBudget() {
        ExperimentManifest manifest = manifest();
        ExperimentEntity experiment = experiment(manifest);
        when(experimentMapper.selectById(1L)).thenReturn(experiment);
        when(testCaseVersionMapper.selectBatchIds(any())).thenReturn(List.of(testCaseVersion()));
        when(variantMapper.selectList(any())).thenReturn(List.of(baseline(), candidate()));
        when(agentFeign.getCandidateConfigIdentity(60L, 2L, "candidate-hash"))
                .thenReturn(Result.ok(new AgentCandidateConfigVO(60L, 2L, 70L, 80L, 90L,
                        3, "source-hash", 3, "candidate-hash", "common-hash",
                        List.of("snapshot.systemPrompt"))));
        when(snapshotService.load(eq(experiment), eq(manifest), any())).thenReturn(snapshot());
        when(evidenceService.load(eq(2L), any(), any())).thenReturn(
                new ExperimentReportEvidenceService.Evidence(List.of(), List.of(
                        new ExperimentReportEvidenceService.CaseFeedback(51L, "baseline", 100L,
                                "MANUAL", "51", "ACCEPTED", null),
                        new ExperimentReportEvidenceService.CaseFeedback(52L, "baseline", 100L,
                                "MANUAL", "52", "REJECTED", null)), List.of(51L, 52L), List.of()));

        service.ensureAutomatic(1L);

        ArgumentCaptor<ExperimentReportPersistenceService.Prepared> saved =
                ArgumentCaptor.forClass(ExperimentReportPersistenceService.Prepared.class);
        verify(persistenceService).save(eq(1L), saved.capture(), eq(null), eq(null), eq(9L));
        JsonNode report = JsonUtils.parse(saved.getValue().reportJson(), JsonNode.class);
        assertEquals(1, report.get("expectedCaseCount").intValue());
        assertEquals(2, report.get("cells").size());
        assertEquals(30, report.get("actualTokenUsage").longValue());
        assertEquals(0, report.get("budgetOverrun").longValue());
        assertEquals(2, report.get("feedback").size());
        assertEquals(1, report.get("feedbackCoverage").get(0).get("coveredCaseCount").intValue());
        assertEquals(2, JsonUtils.parse(saved.getValue().selectedRecordIdsJson(), JsonNode.class)
                .get("metricIds").size());
    }

    @Test
    void activeExperimentNeverGeneratesFinalReport() {
        ExperimentEntity experiment = new ExperimentEntity();
        experiment.setStatus("RUNNING");
        when(experimentMapper.selectById(1L)).thenReturn(experiment);

        service.ensureAutomatic(1L);

        verifyNoInteractions(snapshotService, persistenceService);
    }

    private ExperimentManifest manifest() {
        List<MetricManifest> metrics = List.of(new MetricManifest("execution.total-tokens", "NUMBER",
                "token", false, "LOWER_IS_BETTER", "EXECUTION", null));
        return new ExperimentManifest(3L, List.of(new ManifestCase(100L, 80L, 90L, 1,
                "input", 101L, 0L, "document", List.of())), 3, "source-hash", 1,
                metrics, StableSnapshotUtils.snapshotHash(1, metrics));
    }

    private ExperimentEntity experiment(ExperimentManifest manifest) {
        ExperimentEntity entity = new ExperimentEntity();
        entity.setId(1L);
        entity.setSpaceId(2L);
        entity.setDatasetVersionId(3L);
        entity.setStatus("COMPLETED");
        entity.setManifestSchemaVersion(1);
        entity.setManifestJson(JsonUtils.toJson(manifest));
        entity.setManifestHash(StableSnapshotUtils.snapshotHash(1, manifest));
        entity.setAuthorizedTokenBudget(40L);
        entity.setStartedBy(9L);
        return entity;
    }

    private ExperimentVariantEntity baseline() {
        ExperimentVariantEntity entity = new ExperimentVariantEntity();
        entity.setId(4L);
        entity.setVariantKey("baseline");
        entity.setRole("BASELINE");
        entity.setVariantType("PROMPT");
        entity.setSourceSnapshotSchemaVersion(3);
        entity.setSourceSnapshotHash("source-hash");
        entity.setCandidateSnapshotHash("source-hash");
        entity.setSnapshotWithoutPromptHash("common-hash");
        entity.setEvaluationRunId(10L);
        return entity;
    }

    private ExperimentVariantEntity candidate() {
        ExperimentVariantEntity entity = new ExperimentVariantEntity();
        entity.setId(5L);
        entity.setVariantKey("candidate");
        entity.setRole("CANDIDATE");
        entity.setVariantType("PROMPT");
        entity.setCandidateConfigId(60L);
        entity.setSourceSnapshotSchemaVersion(3);
        entity.setSourceSnapshotHash("source-hash");
        entity.setCandidateSnapshotHash("candidate-hash");
        entity.setCandidateSnapshotSchemaVersion(3);
        entity.setSnapshotWithoutPromptHash("common-hash");
        entity.setPromptDiffFieldPaths("[\"snapshot.systemPrompt\"]");
        entity.setEvaluationRunId(11L);
        return entity;
    }

    private ExperimentReportSnapshotService.Snapshot snapshot() {
        return new ExperimentReportSnapshotService.Snapshot(List.of(
                new ExperimentReportMatrix.VariantMetrics("baseline", 10L,
                        List.of(metric(41L, 10L, 12)), Map.of()),
                new ExperimentReportMatrix.VariantMetrics("candidate", 11L,
                        List.of(metric(42L, 11L, 18)), Map.of())),
                List.of(), List.of(10L, 11L), List.of(31L, 32L), List.of(), List.of(41L, 42L));
    }

    private EvaluationMetricEntity metric(Long id, Long runId, int tokens) {
        EvaluationMetricEntity item = new EvaluationMetricEntity();
        item.setId(id);
        item.setRunId(runId);
        item.setTestCaseVersionId(100L);
        item.setMetricKey("execution.total-tokens");
        item.setContractVersion(1);
        item.setValueType("NUMBER");
        item.setNumericValue(BigDecimal.valueOf(tokens));
        item.setUnit("token");
        item.setDirection("LOWER_IS_BETTER");
        item.setSource("EXECUTION");
        return item;
    }

    private EvaluationTestCaseVersionEntity testCaseVersion() {
        EvaluationTestCaseVersionEntity item = new EvaluationTestCaseVersionEntity();
        item.setId(100L);
        item.setSpaceId(2L);
        item.setSourceTaskId(80L);
        item.setSourceExecutionId(90L);
        item.setSourceInputSchemaVersion(1);
        item.setSourceInputHash("input");
        item.setSourceExecutionSchemaVersion(3);
        item.setSourceExecutionHash("source-hash");
        item.setDocumentVersionSnapshot(0L);
        item.setDocumentContentSha256("document");
        return item;
    }
}
