package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
/** 隐藏分组的评审候选，不提供质量、成本、模板或Task终态线索。 */
public record OnlineReviewAssignmentVO(@Schema(description = "原分配") String assignmentId,
        @Schema(description = "原文档") String documentId) { }
