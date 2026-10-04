package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 执行 Task 在评估领域中的正式关联身份，不包含运行配置或正文。 */
public record EvaluationTaskLinkVO(
        @Schema(description = "执行 Task ID") Long taskId,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "来源 LIVE Task ID") Long sourceTaskId,
        @Schema(description = "测试用例 ID") Long testCaseId,
        @Schema(description = "测试用例版本 ID") Long testCaseVersionId,
        @Schema(description = "用例运行 ID") Long caseRunId,
        @Schema(description = "用例尝试 ID") Long caseAttemptId,
        @Schema(description = "评估运行 ID") Long runId,
        @Schema(description = "实验 ID；普通评估为空") Long experimentId,
        @Schema(description = "实验变体 key；普通评估为空") String variantKey
) { }
