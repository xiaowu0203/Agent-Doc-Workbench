package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Experiment 列表摘要")
public record ExperimentSummaryVO(
        @Schema(description = "Experiment ID") Long id,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "数据集版本 ID") Long datasetVersionId,
        @Schema(description = "数据集名称") String datasetName,
        @Schema(description = "数据集版本号") Integer datasetVersionNo,
        @Schema(description = "Experiment 状态") String status,
        @Schema(description = "Variant 数量") Integer variantCount,
        @Schema(description = "已关联 EvaluationRun 数量") Integer linkedRunCount,
        @Schema(description = "授权 Token 上限") Long authorizedTokenBudget,
        @Schema(description = "稳定失败码") String failureCode,
        @Schema(description = "人工结论") String decision,
        @Schema(description = "人工结论引用的报告 revision") Integer decisionReportRevision,
        @Schema(description = "创建人 ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "开始时间") LocalDateTime startedAt,
        @Schema(description = "结束时间") LocalDateTime finishedAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) { }
