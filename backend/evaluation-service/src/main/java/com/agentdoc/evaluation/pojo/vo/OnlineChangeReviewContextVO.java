package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 隐藏分组的完整原始变更评审投影。 */
public record OnlineChangeReviewContextVO(
        @Schema(description = "分配 ID") String assignmentId,
        @Schema(description = "任务 ID") String taskId,
        @Schema(description = "执行 ID") String executionId,
        @Schema(description = "文档 ID") String documentId,
        @Schema(description = "冻结输入版本") Long baseVersion,
        @Schema(description = "冻结输入正文摘要") String inputHash,
        @Schema(description = "完整冻结输入正文") String inputContent,
        @Schema(description = "完整原始变更集合 ID，与 assignmentId 一致") String originalSetId,
        @Schema(description = "完整原始变更集合的身份摘要，整体评分使用") String originalSetHash,
        @Schema(description = "全部原始产物，不按成功或审批状态筛选") List<Item> originals,
        @Schema(description = "UNKNOWN 或 NON_BLINDED；缺少访问记录不能证明盲态") String knowledgeStatus) {
    public record Item(
            @Schema(description = "原始证据 ID") String evidenceId,
            @Schema(description = "CHANGE_REQUEST 或 DRAFT_DOCUMENT") String evidenceType,
            @Schema(description = "单份原始载荷摘要") String evidenceHash,
            @Schema(description = "原始产物基线版本") Long baseVersion,
            @Schema(description = "草稿收尾版本；提案为 null") Long outputVersion,
            @Schema(description = "原始结构及冻结基线是否有效") boolean structureValid,
            @Schema(description = "原始提案 JSON；草稿为 null") String originalChanges,
            @Schema(description = "原始完整正文；无法应用的提案为 null") String originalContent) { }
}
