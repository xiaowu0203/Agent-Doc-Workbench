package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.ChangeRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 审批队列轻量列表项，不携带正文和变更明细。 */
@Schema(description = "变更审批列表项")
public record ChangeRequestListItemVO(
        Long id,
        Long documentId,
        String documentTitle,
        ChangeRequestStatus status,
        String summary,
        Long sourceTaskId,
        String taskNo,
        String taskName,
        Long agentId,
        String agentName,
        Long assignedReviewerId,
        String assignedReviewerName,
        Integer revisionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
