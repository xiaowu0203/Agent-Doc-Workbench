package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "EvaluationRun 列表摘要")
public record EvaluationRunSummaryVO(
        @Schema(description = "EvaluationRun ID") Long id,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "数据集版本 ID") Long datasetVersionId,
        @Schema(description = "数据集名称") String datasetName,
        @Schema(description = "数据集版本号") Integer datasetVersionNo,
        @Schema(description = "单测试用例版本 ID") Long singleTestCaseVersionId,
        @Schema(description = "测试用例名称") String testCaseName,
        @Schema(description = "测试用例版本号") Integer testCaseVersionNo,
        @Schema(description = "Experiment Variant ID") Long experimentVariantId,
        @Schema(description = "运行状态") String status,
        @Schema(description = "暂停原因") String pauseReason,
        @Schema(description = "是否已请求取消") Boolean cancelRequested,
        @Schema(description = "用例总数") Integer caseCount,
        @Schema(description = "已完成用例数") Integer completedCaseCount,
        @Schema(description = "异常用例数") Integer errorCaseCount,
        @Schema(description = "创建人 ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "开始时间") LocalDateTime startedAt,
        @Schema(description = "结束时间") LocalDateTime finishedAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) { }
