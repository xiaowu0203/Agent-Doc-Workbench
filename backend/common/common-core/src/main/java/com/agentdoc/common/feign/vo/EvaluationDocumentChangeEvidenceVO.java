package com.agentdoc.common.feign.vo;

/**
 * 文档变更 Evaluator 的专项证据；不包含 Artifact payload 或文档正文。
 * <p>
 * 评估阶段校验候选文档变更后产出的轻量证据快照，仅保留哈希与校验状态，
 * 不携带完整文档、变更载荷原文，用于指标计算与回放核验。
 *
 * @param taskId                 关联工作台任务ID
 * @param artifactId            隔离执行产物Artifact主键ID
 * @param artifactSha256        产物整体SHA256哈希，对齐Artifact证据
 * @param valid                 变更语法/结构是否合法
 * @param conflicted            是否存在基线版本冲突
 * @param proposedContentSha256 变更应用完成后目标文档内容的SHA256哈希
 * @param failureCode           失败码，变更校验异常时填充；校验成功可为null
 */
public record EvaluationDocumentChangeEvidenceVO(
        Long taskId,
        Long artifactId,
        String artifactSha256,
        boolean valid,
        boolean conflicted,
        String proposedContentSha256,
        String failureCode) {
}
