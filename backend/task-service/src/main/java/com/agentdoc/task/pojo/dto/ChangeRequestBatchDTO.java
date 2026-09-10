package com.agentdoc.task.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.agentdoc.task.constant.TaskConstant.MAX_CHANGE_REQUEST_BATCH_SIZE;
import static com.agentdoc.task.constant.TaskConstant.MAX_CHANGE_REVIEW_TEXT_LENGTH;

/** 批量整单审批参数。 */
@Schema(description = "批量变更审批参数")
public record ChangeRequestBatchDTO(
        @NotEmpty
        @Size(max = MAX_CHANGE_REQUEST_BATCH_SIZE)
        @Schema(description = "变更请求 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull Long> ids,

        @Size(max = MAX_CHANGE_REVIEW_TEXT_LENGTH)
        @Schema(description = "统一审批意见或原因")
        String reviewComment) {
}
