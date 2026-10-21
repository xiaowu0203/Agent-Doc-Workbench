package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
/** 隐藏分组的评审候选，不提供质量、成本、模板或Task终态线索。 */
public record OnlineReviewAssignmentVO(@Schema(description = "原分配") String assignmentId,
        @Schema(description = "原文档") String documentId,
        @Schema(description = "当前文档标题，仅作定位，不表示原始正文快照名称") String documentTitle,
        @Schema(description = "当前用户在本分配有过人工原始质量评审记录；不表示当前证据或链尾已核验") boolean ownReviewRecorded) { }
