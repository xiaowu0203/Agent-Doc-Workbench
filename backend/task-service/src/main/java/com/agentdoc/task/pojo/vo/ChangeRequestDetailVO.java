package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.feign.dto.ChangeItemDTO;
import com.agentdoc.task.enums.ActorType;
import com.agentdoc.task.enums.ChangeRequestResolutionType;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.ChangeRequestType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 变更审批详情聚合视图。 */
@Schema(description = "变更审批详情")
public record ChangeRequestDetailVO(
        Long id,
        Long spaceId,
        Long documentId,
        String documentTitle,
        ChangeRequestType requestType,
        ChangeRequestStatus status,
        String summary,
        List<ChangeItemDTO> changes,
        Long baseVersion,
        LocalDateTime baseVersionCreatedAt,
        Long currentVersion,
        Long expectedVersion,
        String baseContent,
        String proposedContent,
        String resolvedContent,
        boolean conflicted,
        Long sourceTaskId,
        String taskNo,
        String taskName,
        Long agentId,
        String agentName,
        Long tokensUsed,
        Boolean tokensEstimated,
        String taskResultSummary,
        Long proposedBy,
        ActorType proposedActorType,
        String proposedByName,
        @Schema(description = "触发该审批任务的用户 ID")
        Long triggeredBy,
        @Schema(description = "触发该审批任务的用户名称")
        String triggeredByName,
        Long assignedReviewerId,
        String assignedReviewerName,
        String reviewComment,
        Long reviewedBy,
        String reviewedByName,
        LocalDateTime reviewedAt,
        ChangeRequestResolutionType resolutionType,
        List<String> acceptedChangeKeys,
        Long mergedBy,
        String mergedByName,
        LocalDateTime mergedAt,
        Long mergedVersion,
        Long parentRequestId,
        Integer revisionNo,
        Long reworkTaskId,
        List<ChangeRequestCommentVO> comments,
        List<ChangeRequestAuditVO> auditTrail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
