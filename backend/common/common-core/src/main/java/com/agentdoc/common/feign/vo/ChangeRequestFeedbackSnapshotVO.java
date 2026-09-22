package com.agentdoc.common.feign.vo;

import java.time.LocalDateTime;

/** ChangeRequest 最终审批事实的脱敏、不可变导入投影。 */
public record ChangeRequestFeedbackSnapshotVO(
        Long changeRequestId,
        Long spaceId,
        Long taskId,
        Long executionId,
        String status,
        String resolutionType,
        String reviewCommentHash,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        Integer revisionNo,
        String sourceHash) {
}
