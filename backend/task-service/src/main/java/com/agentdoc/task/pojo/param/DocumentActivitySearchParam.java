package com.agentdoc.task.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 文档活动聚合查询参数。
 */
@Schema(description = "文档活动聚合查询参数")
public record DocumentActivitySearchParam(

        @Schema(description = "文档 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "文档 ID 不能为空")
        Long documentId,

        @Schema(description = "分页参数")
        PageParam pageParam
) {
}
