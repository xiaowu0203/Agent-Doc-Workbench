package com.agentdoc.task.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 变更审批操作参数（通过 / 拒绝 / 退回共用）。
 */
@Schema(description = "变更审批操作参数")
public record ChangeRequestReviewDTO(

        @Schema(description = "审批意见 / 退回批注")
        @Size(max = 500, message = "审批意见最长 500 字符")
        String reviewComment
) {
}
