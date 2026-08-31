package com.agentdoc.task.pojo.param;

import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.task.enums.ChangeRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 审批队列查询参数（过滤条件 + 分页，统一经请求体传递）。
 */
@Schema(description = "审批队列查询参数")
public record ChangeRequestSearchParam(

        @Schema(description = "空间 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "空间 ID 不能为空")
        Long spaceId,

        @Schema(description = "文档 ID（可选）")
        Long documentId,

        @Schema(description = "状态（可选）")
        ChangeRequestStatus status,

        @Schema(description = "分页参数")
        PageParam pageParam
) {
}
