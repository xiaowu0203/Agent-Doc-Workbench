package com.agentdoc.evaluation.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OnlineExperimentDecisionDTO(
        @Schema(description = "操作者范围的幂等键") String clientRequestKey,
        @Schema(description = "冻结报告版本") Integer reportRevision,
        @Schema(description = "冻结报告摘要") String reportHash,
        @Schema(description = "ACCEPTED/REJECTED/INSUFFICIENT_EVIDENCE") String decision,
        @Schema(description = "明确人工理由") String reason,
        @Schema(description = "仅安全REJECTED提供独立已确认反馈身份") String safetyFeedbackId) { }
