package com.agentdoc.document.pojo.param;

import com.agentdoc.common.enums.DocType;
import com.agentdoc.document.enums.DocStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 文档树查询参数。
 * <p>关键词匹配标题；状态为空时只查询正常文档。</p>
 */
@Schema(description = "文档树查询参数")
public record DocumentTreeSearchParam(

        @Schema(description = "空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "空间 ID 不能为空")
        Long spaceId,

        @Schema(description = "标题关键词")
        String keyword,

        @Schema(description = "文档类型")
        DocType docType,

        @Schema(description = "文档状态；为空时查询正常文档")
        DocStatus status
) {
}
