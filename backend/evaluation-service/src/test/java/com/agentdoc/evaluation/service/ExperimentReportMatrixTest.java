package com.agentdoc.evaluation.service;

import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExperimentReportMatrixTest {

    @Test
    void frozenManifestSurvivesJsonRoundTrip() {
        ExperimentManifest manifest = manifest();
        ExperimentManifest restored = JsonUtils.parse(JsonUtils.toJson(manifest), ExperimentManifest.class);

        assertEquals(StableSnapshotUtils.snapshotHash(1, manifest),
                StableSnapshotUtils.snapshotHash(1, restored));
    }

    @Test
    void missingOnBothSidesStillProducesExpectedCells() {
        List<ExperimentReportMatrix.Cell> cells = ExperimentReportMatrix.align(manifest(), List.of(
                new ExperimentReportMatrix.VariantMetrics("baseline", 11L, List.of(), Map.of()),
                new ExperimentReportMatrix.VariantMetrics("candidate", 12L, List.of(), Map.of())));

        assertEquals(6, cells.size());
        assertEquals(List.of("execution.success", "execution.success", "evaluation.quality", "evaluation.quality",
                "execution.cost", "execution.cost"), cells.stream().map(ExperimentReportMatrix.Cell::metricKey).toList());
        assertEquals(6, cells.stream().filter(cell -> "EVIDENCE_MISSING".equals(cell.missingReason())).count());
    }

    @Test
    void mismatchDoesNotBecomeZeroOrComparableValue() {
        EvaluationMetricEntity wrongEvaluator = metric(21L, "evaluation.quality", "BOOLEAN", "boolean", 99L);
        wrongEvaluator.setBooleanValue(true);
        EvaluationMetricEntity wrongContract = metric(22L, "execution.success", "NUMBER", "boolean", null);
        wrongContract.setNumericValue(BigDecimal.ZERO);
        List<ExperimentReportMatrix.Cell> cells = ExperimentReportMatrix.align(manifest(), List.of(
                new ExperimentReportMatrix.VariantMetrics("baseline", 11L,
                        List.of(wrongEvaluator, wrongContract), Map.of())));

        assertEquals("METRIC_CONTRACT_MISMATCH", cells.get(0).missingReason());
        assertEquals("EVALUATOR_VERSION_MISMATCH", cells.get(1).missingReason());
        assertNull(cells.get(0).numericValue());
        assertNull(cells.get(1).booleanValue());
    }

    @Test
    void costRetainsActualCurrency() {
        EvaluationMetricEntity cost = metric(23L, "execution.cost", "NUMBER", "CNY", null);
        cost.setNumericValue(new BigDecimal("0.25"));

        ExperimentReportMatrix.Cell cell = ExperimentReportMatrix.align(manifest(), List.of(
                new ExperimentReportMatrix.VariantMetrics("baseline", 11L, List.of(cost), Map.of())))
                .get(2);

        assertEquals("CNY", cell.unit());
        assertEquals(new BigDecimal("0.25"), cell.numericValue());
        assertNull(cell.missingReason());
    }

    @Test
    void nullMetricValueIsMissingAndCatalogTamperingFails() {
        EvaluationMetricEntity empty = metric(24L, "execution.success", "BOOLEAN", "boolean", null);
        ExperimentReportMatrix.Cell cell = ExperimentReportMatrix.align(manifest(), List.of(
                new ExperimentReportMatrix.VariantMetrics("baseline", 11L, List.of(empty), Map.of())))
                .getFirst();
        assertEquals("EVIDENCE_MISSING", cell.missingReason());

        ExperimentManifest original = manifest();
        ExperimentManifest tampered = new ExperimentManifest(original.datasetVersionId(), original.cases(),
                original.sourceSnapshotSchemaVersion(), original.sourceSnapshotHash(),
                original.metricCatalogVersion(), original.metrics(), "wrong-hash");
        assertThrows(IllegalArgumentException.class, () -> ExperimentReportMatrix.align(tampered, List.of()));
    }

    private ExperimentManifest manifest() {
        List<MetricManifest> metrics = List.of(
                new MetricManifest("execution.success", "BOOLEAN", "boolean", false,
                        "HIGHER_IS_BETTER", "EXECUTION", null),
                new MetricManifest("evaluation.quality", "BOOLEAN", "boolean", false,
                        "HIGHER_IS_BETTER", "EVALUATOR", "quality"),
                new MetricManifest("execution.cost", "NUMBER", "ISO-4217", true,
                        "LOWER_IS_BETTER", "EXECUTION", null));
        return new ExperimentManifest(1L,
                List.of(new ManifestCase(2L, 3L, 4L, 1, "input", 5L, 0L, "document",
                        List.of(new ManifestEvaluator(6L, "quality", "evaluator")))),
                3, "snapshot", 1, metrics, StableSnapshotUtils.snapshotHash(1, metrics));
    }

    private EvaluationMetricEntity metric(Long id, String key, String type, String unit, Long evaluatorId) {
        EvaluationMetricEntity metric = new EvaluationMetricEntity();
        metric.setId(id);
        metric.setTestCaseVersionId(2L);
        metric.setMetricKey(key);
        metric.setContractVersion(1);
        metric.setValueType(type);
        metric.setUnit(unit);
        metric.setDirection(key.equals("execution.cost") ? "LOWER_IS_BETTER" : "HIGHER_IS_BETTER");
        metric.setSource(key.startsWith("execution.") ? "EXECUTION" : "EVALUATOR");
        metric.setEvaluatorVersionId(evaluatorId);
        return metric;
    }
}
