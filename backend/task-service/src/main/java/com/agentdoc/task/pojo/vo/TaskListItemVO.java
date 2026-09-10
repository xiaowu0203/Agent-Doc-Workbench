package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.enums.DocType;
import com.agentdoc.task.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 任务列表展示摘要。
 */
@Schema(description = "任务列表展示摘要")
public record TaskListItemVO(
        @Schema(description = "任务 ID") Long id,
        @Schema(description = "可读任务编号") String taskNo,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "任务名称") String name,
        @Schema(description = "任务状态") TaskStatus status,
        @Schema(description = "Agent ID") Long agentId,
        @Schema(description = "Agent 名称") String agentName,
        @Schema(description = "目标文档 ID") Long documentId,
        @Schema(description = "目标文档标题") String documentTitle,
        @Schema(description = "创建任务时的文档类型") DocType documentType,
        @Schema(description = "Token 预算上限") Long tokenBudget,
        @Schema(description = "已消耗 Token 数") Long tokensUsed,
        @Schema(description = "创建人用户 ID") Long createdBy,
        @Schema(description = "创建人名称") String creatorName,
        @Schema(description = "开始时间") LocalDateTime startTime,
        @Schema(description = "结束时间") LocalDateTime endTime,
        @Schema(description = "创建时间") LocalDateTime createdAt) {
}
