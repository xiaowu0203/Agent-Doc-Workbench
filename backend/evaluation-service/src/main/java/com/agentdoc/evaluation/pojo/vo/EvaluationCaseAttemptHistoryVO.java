package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "CaseRun 的执行尝试与结果历史摘要")
public record EvaluationCaseAttemptHistoryVO(
        @Schema(description = "CaseAttempt ID") Long id,
        @Schema(description = "尝试序号") Integer attemptNo,
        @Schema(description = "是否为当前 CaseAttempt") boolean currentCaseAttempt,
        @Schema(description = "Replay Task ID") Long replayTaskId,
        @Schema(description = "Replay 或 Experiment 执行 Task ID") Long executionTaskId,
        @Schema(description = "尝试状态") String status,
        @Schema(description = "失败阶段") String failureStage,
        @Schema(description = "稳定失败码") String failureCode,
        @Schema(description = "脱敏失败说明") String failureMessage,
        @Schema(description = "开始时间") LocalDateTime startedAt,
        @Schema(description = "结束时间") LocalDateTime finishedAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt,
        @Schema(description = "本次尝试的 EvaluationResult 摘要") List<EvaluationResultSummaryVO> results
) {
    public EvaluationCaseAttemptHistoryVO {
        results = List.copyOf(results);
    }
}
