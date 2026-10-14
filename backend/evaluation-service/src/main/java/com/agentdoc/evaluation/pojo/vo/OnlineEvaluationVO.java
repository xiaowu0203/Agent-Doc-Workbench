package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "线上评价追加结果，不包含原始正文、expected 或诊断详情")
public record OnlineEvaluationVO(
        @Schema(description = "评价尝试身份") String id,
        @Schema(description = "分配身份") String assignmentId,
        @Schema(description = "冻结规则键") String ruleKey,
        @Schema(description = "发布版本身份") String evaluatorVersionId,
        @Schema(description = "追加尝试序号") int attemptNo,
        @Schema(description = "不可变结果身份") String resultId,
        @Schema(description = "PASSED/FAILED/ERROR/SKIPPED，均不改变执行终态") String status,
        @Schema(description = "脱敏结果原因") String reasonCode) { }
