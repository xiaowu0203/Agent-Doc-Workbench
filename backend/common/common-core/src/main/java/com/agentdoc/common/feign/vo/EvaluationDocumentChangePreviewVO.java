package com.agentdoc.common.feign.vo;

/**
 * Evaluation 专用文档变更预览事实；不返回基准正文或提案正文。
 */
public record EvaluationDocumentChangePreviewVO(
        Long documentId,
        Long baseVersion,
        String proposedContentSha256,
        boolean conflicted) {
}
