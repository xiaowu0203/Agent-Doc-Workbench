package com.agentdoc.task.pojo.dto;

import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.task.enums.ChangeRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 提交变更请求参数。
 */
@Schema(description = "提交变更请求")
public record ChangeRequestSubmitDTO(

        @Schema(description = "目标文档 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "文档 ID 不能为空")
        Long documentId,

        @Schema(description = "请求类型：FORMAL 正式 / DRAFT 草稿", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请求类型不能为空")
        ChangeRequestType requestType,

        @Schema(description = "结构化变更列表", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "变更列表不能为空")
        @Valid
        List<ChangeItemDTO> changes,

        @Schema(description = "目标文档基线版本号（合并时校验防并发覆盖）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "基线版本号不能为空")
        Long baseVersion
) {
}
