package com.agentdoc.common.feign.vo;

import java.time.LocalDateTime;

/**
 * 审批页面使用的文档变更预览。
 *
 * @param documentId 文档 ID
 * @param documentTitle 文档标题
 * @param baseVersion 变更基准版本
 * @param currentVersion 当前正式版本
 * @param expectedVersion 无冲突时预计生成的版本
 * @param baseContent 基准版本 Markdown
 * @param baseVersionCreatedAt 基准版本创建时间；旧数据无法恢复时为空
 * @param proposedContent 应用原始变更后的 Markdown
 * @param conflicted 当前版本是否已经偏离基准版本
 */
public record DocumentChangePreviewVO(
        Long documentId,
        String documentTitle,
        Long baseVersion,
        Long currentVersion,
        Long expectedVersion,
        String baseContent,
        LocalDateTime baseVersionCreatedAt,
        String proposedContent,
        boolean conflicted) {
}
