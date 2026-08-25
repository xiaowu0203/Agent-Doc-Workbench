package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.pojo.entity.TaskEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 任务视图。
 */
@Schema(description = "任务信息")
public record TaskVO(
        @Schema(description = "任务 ID") Long id,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "Agent ID") Long agentId,
        @Schema(description = "目标文档 ID") Long documentId,
        @Schema(description = "任务名称") String name,
        @Schema(description = "任务指令") String instruction,
        @Schema(description = "任务状态") TaskStatus status,
        @Schema(description = "Token 预算上限") Long tokenBudget,
        @Schema(description = "已消耗 Token 数") Long tokensUsed,
        @Schema(description = "开始时间") LocalDateTime startTime,
        @Schema(description = "结束时间") LocalDateTime endTime,
        @Schema(description = "最近一次失败原因") String errorMessage,
        @Schema(description = "任务结果摘要") String resultSummary,
        @Schema(description = "创建人用户 ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdAt) {

    public static TaskVO from(TaskEntity entity) {
        return new TaskVO(entity.getId(), entity.getSpaceId(), entity.getAgentId(), entity.getDocumentId(),
                entity.getName(), entity.getInstruction(), TaskStatus.fromCode(entity.getStatus()),
                entity.getTokenBudget(), entity.getTokensUsed(), entity.getStartTime(), entity.getEndTime(),
                entity.getErrorMessage(), entity.getResultSummary(), entity.getCreatedBy(), entity.getCreatedAt());
    }
}
