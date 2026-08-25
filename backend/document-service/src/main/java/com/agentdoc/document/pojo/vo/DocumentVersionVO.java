package com.agentdoc.document.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文档版本视图对象（不含正文快照，用于版本列表）。
 */
@Schema(description = "文档版本信息")
public record DocumentVersionVO(

        @Schema(description = "版本记录 ID")
        Long id,

        @Schema(description = "文档 ID")
        Long documentId,

        @Schema(description = "版本号（从 1 递增）")
        Long versionNo,

        @Schema(description = "变更摘要")
        String changeSummary,

        @Schema(description = "触发人用户 ID")
        Long createdBy,

        @Schema(description = "创建时间")
        LocalDateTime createdAt
) {
}
