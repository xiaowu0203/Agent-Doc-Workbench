package com.agentdoc.document.pojo.vo;

import com.agentdoc.document.enums.DocStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文档目录视图对象。
 */
@Schema(description = "文档目录信息")
public record DocumentDirectoryVO(

        @Schema(description = "目录 ID")
        Long id,

        @Schema(description = "所属空间 ID")
        Long spaceId,

        @Schema(description = "父目录 ID，null 为根目录")
        Long parentId,

        @Schema(description = "目录名称")
        String title,

        @Schema(description = "状态")
        DocStatus status,

        @Schema(description = "创建时间")
        LocalDateTime createdAt,

        @Schema(description = "最后更新时间")
        LocalDateTime updatedAt
) {
}
