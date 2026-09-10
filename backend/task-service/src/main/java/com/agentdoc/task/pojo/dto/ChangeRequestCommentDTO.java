package com.agentdoc.task.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.agentdoc.task.constant.TaskConstant.MAX_CHANGE_REVIEW_TEXT_LENGTH;

/** 新增变更请求批注。 */
@Schema(description = "变更请求批注参数")
public record ChangeRequestCommentDTO(
        @Size(max = 100)
        @Schema(description = "关联 Diff 块标识；空表示整单批注")
        String changeKey,

        @NotBlank
        @Size(max = MAX_CHANGE_REVIEW_TEXT_LENGTH)
        @Schema(description = "批注内容", requiredMode = Schema.RequiredMode.REQUIRED)
        String content) {
}
