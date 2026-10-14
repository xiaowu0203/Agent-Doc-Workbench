package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.time.LocalDateTime;
@Schema(description = "OnlineExperimentSummaryVO")
public record OnlineExperimentSummaryVO(
        @Schema(description = "实验身份") String id,
        @Schema(description = "空间身份") String spaceId,
        @Schema(description = "Agent身份") String agentId,
        @Schema(description = "实验名称") String name,
        @Schema(description = "当前状态") String status,
        @Schema(description = "状态版本") Long stateVersion,
        @Schema(description = "线上协议版本") Integer manifestSchemaVersion,
        @Schema(description = "冻结清单摘要") String manifestHash,
        @Schema(description = "候选分桶权重") Integer candidateWeightBps,
        @Schema(description = "分析模式") String analysisMode,
        @Schema(description = "范围独立文档数") Integer documentCount,
        @Schema(description = "参与文档数") Long participatingDocumentCount,
        @Schema(description = "已分配任务数") Integer assignedTaskCount,
        @Schema(description = "总授权Token") String authorizedTokenBudget,
        @Schema(description = "未结算预留Token") String reservedTokenBudget,
        @Schema(description = "已结算实际Token") String consumedTokens,
        @Schema(description = "基线占槽数") Integer baselineSlotCount,
        @Schema(description = "候选占槽数") Integer candidateSlotCount,
        @Schema(description = "未知任务数") Integer unknownTaskCount,
        @Schema(description = "稳定异常原因") List<String> reasonCodes,
        @Schema(description = "最近对账时间") LocalDateTime lastReconciledAt,
        @Schema(description = "创建者身份") String createdBy,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt
) { }
