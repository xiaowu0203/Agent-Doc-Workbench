package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.common.pojo.vo.PageVO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "当前已核验反馈链，不返回组别、配置、成本和私有事实")
public record OnlineQualityHistoryVO(
        @Schema(description = "原始质量协议版本2") Integer schemaVersion,
        @Schema(description = "空间身份") String spaceId,
        @Schema(description = "实验身份") String experimentId,
        @Schema(description = "分配身份") String assignmentId,
        @Schema(description = "Task身份") String taskId,
        @Schema(description = "Execution身份") String executionId,
        @Schema(description = "原始证据类型") String originalEvidenceType,
        @Schema(description = "原始证据身份") String originalEvidenceId,
        @Schema(description = "原始证据摘要") String originalEvidenceHash,
        @Schema(description = "当次读取的链尾；无反馈为空") String currentTailFeedbackId,
        @Schema(description = "当次链尾有效知情状态；无反馈为空") String currentTailBlindness,
        @Schema(description = "调用者最后一条反馈，用于未知响应核对；无本人反馈为空") Item lastOwnFeedback,
        @Schema(description = "按更正顺序分页的评分及身份") PageVO<Item> history) {
    public record Item(
            @Schema(description = "反馈身份") String id,
            @Schema(description = "不可变反馈摘要") String sourceHash,
            @Schema(description = "前序反馈身份；首评为空") String supersedesFeedbackId,
            @Schema(description = "质量等级0～4") Integer rubricScore,
            @Schema(description = "质量标签") String label,
            @Schema(description = "服务端有效知情状态") String blindness,
            @Schema(description = "作者声明的知情状态") String declaredBlindness,
            @Schema(description = "是否为当前调用者的反馈") Boolean ownFeedback,
            @Schema(description = "自由文本，仅作者可见") String comment,
            @Schema(description = "原因代码，仅作者可见；其他作者为空列表") List<String> reasonCodes,
            @Schema(description = "严重安全确认事实") Boolean seriousSafetyEvent,
            @Schema(description = "安全理由，仅作者可见") String safetyReason,
            @Schema(description = "创建时间") LocalDateTime createdAt) { }
}
