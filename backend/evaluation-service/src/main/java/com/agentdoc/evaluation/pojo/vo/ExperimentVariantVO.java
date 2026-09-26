package com.agentdoc.evaluation.pojo.vo;

import java.util.List;

/** 不暴露 Prompt 正文和完整快照的 Experiment Variant 投影。 */
public record ExperimentVariantVO(Long id, Long experimentId, String variantKey, String role,
                                  String type, Long candidateConfigRef,
                                  Integer sourceSnapshotSchemaVersion, String sourceSnapshotHash,
                                  Integer candidateSnapshotSchemaVersion, String candidateSnapshotHash,
                                  List<String> promptDiffFieldPaths, String snapshotWithoutPromptHash,
                                  Long evaluationRunId) {
}
