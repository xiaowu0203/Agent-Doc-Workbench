package com.agentdoc.task.pojo.dto;

import com.agentdoc.task.enums.ChangeRequestResolutionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.agentdoc.task.constant.TaskConstant.MAX_ACCEPTED_CHANGE_KEY_COUNT;
import static com.agentdoc.task.constant.TaskConstant.MAX_CHANGE_REVIEW_TEXT_LENGTH;

/** 通过变更请求时提交的内容决议。 */
@Schema(description = "变更请求通过参数")
public record ChangeRequestApproveDTO(
        @NotNull
        @Schema(description = "接受方式", requiredMode = Schema.RequiredMode.REQUIRED)
        ChangeRequestResolutionType resolutionType,

        @NotBlank
        @Size(max = MAX_CHANGE_REVIEW_TEXT_LENGTH)
        @Schema(description = "审批意见", requiredMode = Schema.RequiredMode.REQUIRED)
        String reviewComment,

        @Size(max = MAX_ACCEPTED_CHANGE_KEY_COUNT)
        @Schema(description = "PARTIAL 模式下接受的 Diff 块标识")
        List<@NotBlank @Size(max = 100) String> acceptedChangeKeys,

        @Schema(description = "PARTIAL / EDITED 模式下审批人确认的最终 Markdown")
        String resolvedContent) {
}
