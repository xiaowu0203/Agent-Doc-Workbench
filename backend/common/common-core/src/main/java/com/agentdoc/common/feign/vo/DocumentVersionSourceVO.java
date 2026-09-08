package com.agentdoc.common.feign.vo;

import java.time.LocalDateTime;

/**
 * 文档版本关联的审批与任务展示投影。
 */
public record DocumentVersionSourceVO(
        Long sourceChangeRequestId,
        Long sourceTaskId,
        String taskNo,
        String taskName,
        Long agentId,
        String agentName,
        Long triggeredBy,
        String triggeredByName,
        Long tokensUsed,
        Boolean tokensEstimated,
        Long reviewedBy,
        String reviewedByName,
        LocalDateTime reviewedAt,
        Long mergedBy,
        String mergedByName,
        LocalDateTime mergedAt,
        Boolean executionAvailable) {
}
