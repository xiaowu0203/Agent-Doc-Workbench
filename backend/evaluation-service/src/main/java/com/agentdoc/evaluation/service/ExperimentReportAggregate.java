package com.agentdoc.evaluation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 报告聚合只消费已按冻结契约对齐的单元；缺失不参与数值计算。 */
final class ExperimentReportAggregate {

    private static final int MEAN_SCALE = 10;

    private ExperimentReportAggregate() { }

    static List<VariantSummary> summarize(ExperimentManifest manifest,
                                          List<ExperimentReportMatrix.Cell> cells) {
        Map<String, MetricManifest> contracts = manifest.metrics().stream()
                .collect(Collectors.toMap(MetricManifest::metricKey, item -> item));
        Map<SummaryKey, List<ExperimentReportMatrix.Cell>> groups = new LinkedHashMap<>();
        for (ExperimentReportMatrix.Cell cell : cells) {
            groups.computeIfAbsent(new SummaryKey(cell.variantKey(), cell.metricKey()), key -> new ArrayList<>())
                    .add(cell);
        }
        List<VariantSummary> summaries = new ArrayList<>();
        for (Map.Entry<SummaryKey, List<ExperimentReportMatrix.Cell>> entry : groups.entrySet()) {
            MetricManifest contract = contracts.get(entry.getKey().metricKey());
            List<ExperimentReportMatrix.Cell> values = entry.getValue();
            long valid = values.stream().filter(cell -> cell.missingReason() == null).count();
            long trueCount = values.stream().filter(cell -> Boolean.TRUE.equals(cell.booleanValue())).count();
            long falseCount = values.stream().filter(cell -> Boolean.FALSE.equals(cell.booleanValue())).count();
            Map<String, Long> missingReasons = values.stream()
                    .filter(cell -> cell.missingReason() != null)
                    .collect(Collectors.groupingBy(ExperimentReportMatrix.Cell::missingReason,
                            LinkedHashMap::new, Collectors.counting()));
            Map<String, BigDecimal> totals = new LinkedHashMap<>();
            Map<String, Long> numericCounts = new LinkedHashMap<>();
            for (ExperimentReportMatrix.Cell cell : values) {
                if (cell.missingReason() != null || cell.numericValue() == null) {
                    continue;
                }
                String unit = contract.currencyUnit() ? cell.unit() : contract.unit();
                totals.merge(unit, cell.numericValue(), BigDecimal::add);
                numericCounts.merge(unit, 1L, Long::sum);
            }
            Map<String, BigDecimal> means = new LinkedHashMap<>();
            totals.forEach((unit, total) -> means.put(unit,
                    total.divide(BigDecimal.valueOf(numericCounts.get(unit)), MEAN_SCALE, RoundingMode.HALF_UP)));
            summaries.add(new VariantSummary(entry.getKey().variantKey(), contract.metricKey(),
                    values.size(), valid, values.size() - valid, trueCount, falseCount,
                    Collections.unmodifiableMap(totals), Collections.unmodifiableMap(means),
                    Collections.unmodifiableMap(missingReasons)));
        }
        return List.copyOf(summaries);
    }

    static List<PairSummary> compare(ExperimentManifest manifest,
                                     List<ExperimentReportMatrix.Cell> cells) {
        Map<String, MetricManifest> contracts = manifest.metrics().stream()
                .collect(Collectors.toMap(MetricManifest::metricKey, item -> item));
        Map<CellKey, ExperimentReportMatrix.Cell> baseline = cells.stream()
                .filter(cell -> "baseline".equals(cell.variantKey()))
                .collect(Collectors.toMap(cell -> new CellKey(cell.testCaseVersionId(), cell.metricKey()),
                        cell -> cell));
        Map<SummaryKey, List<ExperimentReportMatrix.Cell>> candidates = cells.stream()
                .filter(cell -> !"baseline".equals(cell.variantKey()))
                .collect(Collectors.groupingBy(cell -> new SummaryKey(cell.variantKey(), cell.metricKey()),
                        LinkedHashMap::new, Collectors.toList()));
        List<PairSummary> summaries = new ArrayList<>();
        for (Map.Entry<SummaryKey, List<ExperimentReportMatrix.Cell>> entry : candidates.entrySet()) {
            MetricManifest contract = contracts.get(entry.getKey().metricKey());
            int paired = 0;
            int incomparable = 0;
            int improved = 0;
            int worsened = 0;
            int unchanged = 0;
            BigDecimal deltaTotal = BigDecimal.ZERO;
            Map<String, BigDecimal> currencyDeltas = new LinkedHashMap<>();
            Map<String, Integer> currencyCounts = new LinkedHashMap<>();
            for (ExperimentReportMatrix.Cell candidate : entry.getValue()) {
                ExperimentReportMatrix.Cell base = baseline.get(
                        new CellKey(candidate.testCaseVersionId(), candidate.metricKey()));
                if (base == null || base.missingReason() != null || candidate.missingReason() != null) {
                    continue;
                }
                if (contract.currencyUnit() && !Objects.equals(base.unit(), candidate.unit())) {
                    incomparable++;
                    continue;
                }
                int change;
                if ("NUMBER".equals(contract.valueType())) {
                    BigDecimal delta = candidate.numericValue().subtract(base.numericValue());
                    if (contract.currencyUnit()) {
                        currencyDeltas.merge(candidate.unit(), delta, BigDecimal::add);
                        currencyCounts.merge(candidate.unit(), 1, Integer::sum);
                    } else {
                        deltaTotal = deltaTotal.add(delta);
                    }
                    change = delta.signum();
                } else if ("BOOLEAN".equals(contract.valueType())) {
                    change = Boolean.compare(candidate.booleanValue(), base.booleanValue());
                } else {
                    continue;
                }
                paired++;
                int directed = "LOWER_IS_BETTER".equals(contract.direction()) ? -change : change;
                if (directed > 0) {
                    improved++;
                } else if (directed < 0) {
                    worsened++;
                } else {
                    unchanged++;
                }
            }
            Map<String, BigDecimal> currencyMeans = new LinkedHashMap<>();
            currencyDeltas.forEach((unit, total) -> currencyMeans.put(unit,
                    total.divide(BigDecimal.valueOf(currencyCounts.get(unit)), MEAN_SCALE, RoundingMode.HALF_UP)));
            summaries.add(new PairSummary(entry.getKey().variantKey(), contract.metricKey(), paired,
                    incomparable, improved, worsened, unchanged,
                    "NUMBER".equals(contract.valueType()) && !contract.currencyUnit() && paired > 0
                            ? deltaTotal.divide(BigDecimal.valueOf(paired), MEAN_SCALE, RoundingMode.HALF_UP)
                            : null, Collections.unmodifiableMap(currencyMeans)));
        }
        return List.copyOf(summaries);
    }

    record VariantSummary(String variantKey, String metricKey, long expectedCount, long validCount,
                          long missingCount, long trueCount, long falseCount,
                          Map<String, BigDecimal> numericTotalsByUnit,
                          Map<String, BigDecimal> numericMeansByUnit,
                          Map<String, Long> missingReasons) { }

    record PairSummary(String candidateVariantKey, String metricKey, long pairedCount,
                       long incomparableCurrencyCount, long improvedCount, long worsenedCount,
                       long unchangedCount, BigDecimal meanCandidateMinusBaseline,
                       Map<String, BigDecimal> meanCandidateMinusBaselineByCurrency) { }

    private record SummaryKey(String variantKey, String metricKey) { }
    private record CellKey(Long caseId, String metricKey) { }
}
