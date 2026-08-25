package com.agentdoc.document.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档版本详情视图对象（含 Markdown 快照，用于查看 / 对比）。
 */
@Schema(description = "文档版本详情")
public record DocumentVersionDetailVO(

        @Schema(description = "文档 ID")
        Long documentId,

        @Schema(description = "版本号")
        Long versionNo,

        @Schema(description = "该版本 Markdown 快照")
        String content,

        @Schema(description = "变更摘要")
        String changeSummary,

        @Schema(description = "触发人用户 ID")
        Long createdBy
) {
}
