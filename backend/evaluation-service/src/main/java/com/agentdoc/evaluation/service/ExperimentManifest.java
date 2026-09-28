package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.metric.MetricDefinition;

import java.util.List;

/** 创建时冻结的实验报告分母与指标契约。 */
record ExperimentManifest(Long datasetVersionId, List<ManifestCase> cases,
                          Integer sourceSnapshotSchemaVersion, String sourceSnapshotHash,
                          Integer metricCatalogVersion, List<MetricManifest> metrics,
                          String metricCatalogHash) { }

record ManifestCase(Long testCaseVersionId, Long sourceTaskId, Long sourceExecutionId,
                    Integer inputSnapshotSchemaVersion, String inputSnapshotHash,
                    Long documentId, Long documentVersionSnapshot,
                    String documentContentSha256, List<ManifestEvaluator> evaluators) { }

record ManifestEvaluator(Long evaluatorVersionId, String evaluatorKey, String contentHash) { }

record MetricManifest(String metricKey, String valueType, String unit, boolean currencyUnit,
                      String direction, String source, String evaluatorKey) {
    static MetricManifest from(MetricDefinition definition) {
        return new MetricManifest(definition.metricKey(), definition.valueType().name(), definition.unit(),
                definition.currencyUnit(), definition.direction().name(), definition.source().name(),
                definition.evaluatorKey());
    }
}
