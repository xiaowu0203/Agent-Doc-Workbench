package com.agentdoc.document.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档未提交草稿。
 */
@Schema(description = "文档未提交草稿")
public record DocumentDraftVO(

        @Schema(description = "文档 ID")
        Long documentId,

        @Schema(description = "草稿基于的文档版本号")
        Long baseVersion,

        @Schema(description = "草稿标题")
        String title,

        @Schema(description = "Markdown 草稿内容")
        String content
) {
}
