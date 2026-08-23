package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.ChangeRequestType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 变更请求视图对象。
 */
@Schema(description = "变更请求信息")
public record ChangeRequestVO(

        @Schema(description = "变更请求 ID")
        Long id,

        @Schema(description = "目标文档 ID")
        Long documentId,

        @Schema(description = "目标文档标题")
        String documentTitle,

        @Schema(description = "请求类型")
        ChangeRequestType requestType,

        @Schema(description = "结构化变更列表")
        List<ChangeItemDTO> changes,

        @Schema(description = "状态")
        ChangeRequestStatus status,

        @Schema(description = "来源任务 ID（Agent 任务提交时存在）")
        Long sourceTaskId,

        @Schema(description = "提交人（用户或 Agent ID）")
        Long proposedBy,

        @Schema(description = "审批意见")
        String reviewComment,

        @Schema(description = "创建时间")
        LocalDateTime createdAt
) {
}
