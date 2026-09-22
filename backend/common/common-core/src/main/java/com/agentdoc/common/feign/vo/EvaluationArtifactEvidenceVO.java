package com.agentdoc.common.feign.vo;

/** Evaluation 使用的不可变 Artifact 元数据，不包含 payload 正文。 */
public record EvaluationArtifactEvidenceVO(
        Long id,
        Integer sequenceNo,
        String artifactType,
        Integer schemaVersion,
        String payloadSha256,
        Long sourceToolCallId) {
}
