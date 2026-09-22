package com.agentdoc.common.feign.vo;

/**
 * Evaluation 专用文档变更预览事实；不返回基准正文或提案正文。
 * <p>
 * 对候选变更做基线预应用后的结果快照，仅输出校验状态与内容哈希，
 * 不返回完整文档文本，用于评估指标计算与一致性核验。
 *
 * @param documentId             目标文档ID
 * @param baseVersion            变更所基于的文档基线版本号
 * @param proposedContentSha256 变更应用之后文档全文的SHA256哈希
 * @param conflicted             是否发生基线冲突，true代表变更无法安全合并
 */
public record EvaluationDocumentChangePreviewVO(
        Long documentId,
        Long baseVersion,
        String proposedContentSha256,
        boolean conflicted) {
}
