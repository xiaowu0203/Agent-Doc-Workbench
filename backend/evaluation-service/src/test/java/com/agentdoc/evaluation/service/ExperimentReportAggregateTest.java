package com.agentdoc.evaluation.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExperimentReportAggregateTest {

    @Test
    void missingIsNotZeroAndBooleanDirectionUsesAllExpectedCases() {
        List<ExperimentReportMatrix.Cell> cells = List.of(
                cell(1L, "execution.success", "baseline", null, true, null, null),
                cell(2L, "execution.success", "baseline", null, null, null, "EVIDENCE_MISSING"),
                cell(1L, "execution.success", "candidate", null, false, null, null),
                cell(2L, "execution.success", "candidate", null, true, null, null));

        List<ExperimentReportAggregate.VariantSummary> summaries =
                ExperimentReportAggregate.summarize(manifest(), cells);
        ExperimentReportAggregate.VariantSummary baseline = summaries.getFirst();
        assertEquals(2, baseline.expectedCount());
        assertEquals(1, baseline.validCount());
        assertEquals(1, baseline.missingCount());
        assertEquals(1, baseline.trueCount());
        assertEquals(0, baseline.falseCount());
        assertEquals(1L, baseline.missingReasons().get("EVIDENCE_MISSING"));

        ExperimentReportAggregate.PairSummary comparison =
                ExperimentReportAggregate.compare(manifest(), cells).getFirst();
        assertEquals(1, comparison.pairedCount());
        assertEquals(1, comparison.worsenedCount());
    }

    @Test
    void numericMeanUsesOwnDenominatorAndPairOnlyUsesSharedSamples() {
        List<ExperimentReportMatrix.Cell> cells = List.of(
                cell(1L, "evaluation.quality", "baseline", new BigDecimal("0.2"), null, "ratio", null),
                cell(2L, "evaluation.quality", "baseline", null, null, null, "EVALUATION_FAILED"),
                cell(1L, "evaluation.quality", "candidate", new BigDecimal("0.8"), null, "ratio", null),
                cell(2L, "evaluation.quality", "candidate", new BigDecimal("0.4"), null, "ratio", null));

        List<ExperimentReportAggregate.VariantSummary> summaries =
                ExperimentReportAggregate.summarize(manifest(), cells);
        assertEquals(new BigDecimal("0.2000000000"), summaries.get(0).numericMeansByUnit().get("ratio"));
        assertEquals(new BigDecimal("0.6000000000"), summaries.get(1).numericMeansByUnit().get("ratio"));
        ExperimentReportAggregate.PairSummary comparison =
                ExperimentReportAggregate.compare(manifest(), cells).getFirst();
        assertEquals(1, comparison.pairedCount());
        assertEquals(new BigDecimal("0.6000000000"), comparison.meanCandidateMinusBaseline());
    }

    @Test
    void differentCurrenciesNeverSumOrPair() {
        List<ExperimentReportMatrix.Cell> cells = List.of(
                cell(1L, "execution.cost", "baseline", new BigDecimal("1.2"), null, "USD", null),
                cell(2L, "execution.cost", "baseline", new BigDecimal("2.3"), null, "CNY", null),
                cell(1L, "execution.cost", "candidate", new BigDecimal("0.8"), null, "CNY", null),
                cell(2L, "execution.cost", "candidate", new BigDecimal("1.1"), null, "CNY", null));

        List<ExperimentReportAggregate.VariantSummary> summaries =
                ExperimentReportAggregate.summarize(manifest(), cells);
        assertEquals(new BigDecimal("1.2"), summaries.getFirst().numericTotalsByUnit().get("USD"));
        assertEquals(new BigDecimal("2.3"), summaries.getFirst().numericTotalsByUnit().get("CNY"));
        ExperimentReportAggregate.PairSummary comparison =
                ExperimentReportAggregate.compare(manifest(), cells).getFirst();
        assertEquals(1, comparison.incomparableCurrencyCount());
        assertEquals(1, comparison.pairedCount());
        assertNull(comparison.meanCandidateMinusBaseline());
        assertEquals(new BigDecimal("-1.2000000000"),
                comparison.meanCandidateMinusBaselineByCurrency().get("CNY"));
    }

    @Test
    void pairedCostsInDifferentCurrenciesHaveSeparateMeans() {
        List<ExperimentReportMatrix.Cell> cells = List.of(
                cell(1L, "execution.cost", "baseline", new BigDecimal("1.2"), null, "USD", null),
                cell(2L, "execution.cost", "baseline", new BigDecimal("2.3"), null, "CNY", null),
                cell(1L, "execution.cost", "candidate", new BigDecimal("0.8"), null, "USD", null),
                cell(2L, "execution.cost", "candidate", new BigDecimal("1.1"), null, "CNY", null));

        ExperimentReportAggregate.PairSummary comparison =
                ExperimentReportAggregate.compare(manifest(), cells).getFirst();
        assertEquals(2, comparison.pairedCount());
        assertNull(comparison.meanCandidateMinusBaseline());
        assertEquals(new BigDecimal("-0.4000000000"),
                comparison.meanCandidateMinusBaselineByCurrency().get("USD"));
        assertEquals(new BigDecimal("-1.2000000000"),
                comparison.meanCandidateMinusBaselineByCurrency().get("CNY"));
    }

    private ExperimentReportMatrix.Cell cell(Long caseId, String key, String variant,
                                             BigDecimal number, Boolean bool, String unit, String missing) {
        return new ExperimentReportMatrix.Cell(caseId, key, null, variant,
                "baseline".equals(variant) ? 11L : 12L, missing == null ? caseId : null,
                number, bool, null, unit, missing);
    }

    private ExperimentManifest manifest() {
        return new ExperimentManifest(1L, List.of(), 3, "snapshot", 1,
                List.of(new MetricManifest("execution.success", "BOOLEAN", "boolean", false,
                                "HIGHER_IS_BETTER", "EXECUTION", null),
                        new MetricManifest("evaluation.quality", "NUMBER", "ratio", false,
                                "HIGHER_IS_BETTER", "EVALUATOR", "quality"),
                        new MetricManifest("execution.cost", "NUMBER", "ISO-4217", true,
                                "LOWER_IS_BETTER", "EXECUTION", null)), "catalog");
    }
}
