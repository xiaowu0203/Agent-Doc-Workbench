package com.agentdoc.evaluation.service;

import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 以冻结 manifest 为分母对齐实验指标，不从实际 Attempt 推断应有行。 */
final class ExperimentReportMatrix {

    private ExperimentReportMatrix() { }

    static List<Cell> align(ExperimentManifest manifest, List<VariantMetrics> variants) {
        if (!Objects.equals(manifest.metricCatalogHash(),
                StableSnapshotUtils.snapshotHash(manifest.metricCatalogVersion(), manifest.metrics()))) {
            throw new IllegalArgumentException("冻结 Metric 目录 hash 不一致");
        }
        List<IndexedVariant> indexed = variants.stream().map(variant -> new IndexedVariant(variant,
                variant.metrics().stream().collect(Collectors.groupingBy(metric ->
                        new MetricKey(metric.getTestCaseVersionId(), metric.getMetricKey()))))).toList();
        List<Cell> cells = new ArrayList<>();
        for (ManifestCase caseItem : manifest.cases()) {
            Map<String, ManifestEvaluator> evaluators = new HashMap<>();
            for (ManifestEvaluator evaluator : caseItem.evaluators()) {
                if (evaluators.put(evaluator.evaluatorKey(), evaluator) != null) {
                    throw new IllegalArgumentException("同一用例存在重复 Evaluator key");
                }
            }
            for (MetricManifest metric : manifest.metrics()) {
                ManifestEvaluator evaluator = evaluators.get(metric.evaluatorKey());
                if ("EVALUATOR".equals(metric.source()) && evaluator == null) {
                    continue;
                }
                for (IndexedVariant variant : indexed) {
                    cells.add(cell(manifest, caseItem.testCaseVersionId(), metric,
                            evaluator == null ? null : evaluator.evaluatorVersionId(), variant));
                }
            }
        }
        return List.copyOf(cells);
    }

    private static Cell cell(ExperimentManifest manifest, Long caseId, MetricManifest contract,
                             Long evaluatorVersionId, IndexedVariant indexed) {
        VariantMetrics variant = indexed.variant();
        String evaluatorIssue = evaluatorVersionId == null ? null
                : variant.evaluatorIssues().get(new EvaluatorKey(caseId, evaluatorVersionId));
        List<EvaluationMetricEntity> found = indexed.metrics().getOrDefault(
                new MetricKey(caseId, contract.metricKey()), List.of());
        String reason = evaluatorIssue == null
                ? variant.missingReasons().getOrDefault(caseId, "EVIDENCE_MISSING") : evaluatorIssue;
        if ("EVALUATOR_VERSION_MISMATCH".equals(evaluatorIssue)) {
            return new Cell(caseId, contract.metricKey(), evaluatorVersionId, variant.variantKey(),
                    variant.runId(), null, null, null, null, null, evaluatorIssue);
        }
        if (found.isEmpty()) {
            return new Cell(caseId, contract.metricKey(), evaluatorVersionId, variant.variantKey(),
                    variant.runId(), null, null, null, null, null, reason);
        }
        if (found.size() != 1) {
            return new Cell(caseId, contract.metricKey(), evaluatorVersionId, variant.variantKey(),
                    variant.runId(), null, null, null, null, null, "METRIC_CONTRACT_MISMATCH");
        }
        EvaluationMetricEntity actual = found.getFirst();
        if (evaluatorIssue != null) {
            reason = evaluatorIssue;
        } else if (!Objects.equals(evaluatorVersionId, actual.getEvaluatorVersionId())) {
            reason = "EVALUATOR_VERSION_MISMATCH";
        } else if (!Objects.equals(manifest.metricCatalogVersion(), actual.getContractVersion())
                || !Objects.equals(contract.valueType(), actual.getValueType())
                || !Objects.equals(contract.direction(), actual.getDirection())
                || !Objects.equals(contract.source(), actual.getSource())
                || (!contract.currencyUnit() && !Objects.equals(contract.unit(), actual.getUnit()))
                || (contract.currencyUnit() && (actual.getUnit() == null
                || !actual.getUnit().matches("[A-Z]{3}")))) {
            reason = "METRIC_CONTRACT_MISMATCH";
        } else if (("NUMBER".equals(contract.valueType()) && actual.getNumericValue() == null)
                || ("BOOLEAN".equals(contract.valueType()) && actual.getBooleanValue() == null)
                || ("STRING".equals(contract.valueType()) && actual.getStringValue() == null)) {
            reason = "EVIDENCE_MISSING";
        } else {
            reason = null;
        }
        return new Cell(caseId, contract.metricKey(), evaluatorVersionId, variant.variantKey(),
                variant.runId(), actual.getId(), reason == null ? actual.getNumericValue() : null,
                reason == null ? actual.getBooleanValue() : null,
                reason == null ? actual.getStringValue() : null,
                reason == null ? actual.getUnit() : null, reason);
    }

    record VariantMetrics(String variantKey, Long runId, List<EvaluationMetricEntity> metrics,
                          Map<Long, String> missingReasons, Map<EvaluatorKey, String> evaluatorIssues) {
        VariantMetrics(String variantKey, Long runId, List<EvaluationMetricEntity> metrics,
                       Map<Long, String> missingReasons) {
            this(variantKey, runId, metrics, missingReasons, Map.of());
        }
    }

    record EvaluatorKey(Long caseId, Long evaluatorVersionId) { }

    private record MetricKey(Long caseId, String metricKey) { }

    private record IndexedVariant(VariantMetrics variant,
                                  Map<MetricKey, List<EvaluationMetricEntity>> metrics) { }

    record Cell(Long testCaseVersionId, String metricKey, Long evaluatorVersionId,
                String variantKey, Long runId, Long metricId, BigDecimal numericValue,
                Boolean booleanValue, String stringValue, String unit, String missingReason) { }
}
