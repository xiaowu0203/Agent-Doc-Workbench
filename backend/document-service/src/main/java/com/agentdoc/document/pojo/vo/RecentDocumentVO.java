package com.agentdoc.document.pojo.vo;

import com.agentdoc.common.enums.DocType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 最近文档列表项。
 */
@Schema(description = "最近文档信息")
public record RecentDocumentVO(

        @Schema(description = "文档 ID")
        Long id,

        @Schema(description = "文档名称")
        String title,

        @Schema(description = "文档类型")
        DocType docType,

        @Schema(description = "最近更新时间")
        LocalDateTime updatedAt,

        @Schema(description = "更新人名称")
        String updatedByName
) {
}
