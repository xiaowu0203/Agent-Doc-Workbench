package com.agentdoc.document.pojo.vo;

import com.agentdoc.document.enums.DocStatus;
import com.agentdoc.common.enums.DocType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文档列表视图对象（不含正文内容，用于目录树 / 列表）。
 */
@Schema(description = "文档列表信息")
public record DocumentVO(

        @Schema(description = "文档 ID")
        Long id,

        @Schema(description = "所属空间 ID")
        Long spaceId,

        @Schema(description = "所属目录 ID，null 为空间根层")
        Long directoryId,

        @Schema(description = "标题")
        String title,

        @Schema(description = "文档类型")
        DocType docType,

        @Schema(description = "当前版本号")
        Long version,

        @Schema(description = "状态")
        DocStatus status,

        @Schema(description = "最后更新时间")
        LocalDateTime updatedAt,

        @Schema(description = "最后更新人用户 ID")
        Long updatedBy
) {
}
