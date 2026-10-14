package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 原始文本评审投影；不含分组、模板、Prompt、成本或审批后内容。 */
@Schema(description = "隐藏分组的原始文本评审上下文，未记录知情不表示盲态")
public record OnlineTextReviewContextVO(
        @Schema(description = "assignment ID，十进制文本") String assignmentId,
        @Schema(description = "原 Task ID，十进制文本") String taskId,
        @Schema(description = "实际 Execution ID，十进制文本") String executionId,
        @Schema(description = "原文档 ID，十进制文本") String documentId,
        @Schema(description = "冻结文档版本") Long baseVersion,
        @Schema(description = "冻结文档正文摘要") String inputHash,
        @Schema(description = "完整冻结文档正文，仅当前资源授权后返回") String inputContent,
        @Schema(description = "原始文本证据 ID，十进制文本") String originalEvidenceId,
        @Schema(description = "完整原始文本摘要") String originalEvidenceHash,
        @Schema(description = "完整原始最终文本，仅当前资源授权后返回") String originalText,
        @Schema(description = "已知 NON_BLINDED 或无法确认的 UNKNOWN，不声明 BLINDED") String knowledgeStatus) { }
