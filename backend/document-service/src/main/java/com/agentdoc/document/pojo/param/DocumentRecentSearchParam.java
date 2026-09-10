package com.agentdoc.document.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 最近文档查询参数。
 */
@Schema(description = "最近文档查询参数")
public record DocumentRecentSearchParam(

        @Schema(description = "空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "空间 ID 不能为空")
        Long spaceId,

        @Schema(description = "分页参数")
        PageParam pageParam
) {
}
