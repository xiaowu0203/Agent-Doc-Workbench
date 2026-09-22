package com.agentdoc.common.feign.vo;

/** 文档变更 Evaluator 的专项证据；不包含 Artifact payload 或文档正文。 */
public record EvaluationDocumentChangeEvidenceVO(
        Long taskId,
        Long artifactId,
        String artifactSha256,
        boolean valid,
        boolean conflicted,
        String proposedContentSha256,
        String failureCode) {
}
