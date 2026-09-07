package com.agentdoc.task.pojo.vo;

import com.agentdoc.common.enums.DocType;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务视图。
 */
@Schema(description = "任务信息")
public record TaskVO(
        @Schema(description = "任务 ID") Long id,
        @Schema(description = "可读任务编号") String taskNo,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "Agent ID") Long agentId,
        @Schema(description = "目标文档 ID") Long documentId,
        @Schema(description = "创建任务时的文档类型") DocType documentType,
        @Schema(description = "任务名称") String name,
        @Schema(description = "任务指令") String instruction,
        @Schema(description = "任务状态") TaskStatus status,
        @Schema(description = "Token 预算上限") Long tokenBudget,
        @Schema(description = "文档读取范围") TaskReadScope readScope,
        @Schema(description = "关注区域") List<TaskFocusRegionVO> focusRegions,
        @Schema(description = "已消耗 Token 数") Long tokensUsed,
        @Schema(description = "开始时间") LocalDateTime startTime,
        @Schema(description = "派发时间") LocalDateTime dispatchedAt,
        @Schema(description = "最近一次心跳时间") LocalDateTime lastHeartbeatAt,
        @Schema(description = "结束时间") LocalDateTime endTime,
        @Schema(description = "消息重试次数") Integer retryCount,
        @Schema(description = "最近一次失败原因") String errorMessage,
        @Schema(description = "任务结果摘要") String resultSummary,
        @Schema(description = "创建人用户 ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdAt) {

    public static TaskVO from(TaskEntity entity) {
        return new TaskVO(entity.getId(), entity.getTaskNo(), entity.getSpaceId(), entity.getAgentId(),
                entity.getDocumentId(), DocType.fromCode(entity.getDocumentType()), entity.getName(), entity.getInstruction(),
                TaskStatus.fromCode(entity.getStatus()), entity.getTokenBudget(), readScope(entity), focusRegions(entity),
                entity.getTokensUsed(), entity.getStartTime(), entity.getDispatchedAt(), entity.getLastHeartbeatAt(),
                entity.getEndTime(), entity.getRetryCount(),
                entity.getErrorMessage(), entity.getResultSummary(), entity.getCreatedBy(), entity.getCreatedAt());
    }

    private static TaskReadScope readScope(TaskEntity entity) {
        return entity.getReadScope() == null ? TaskReadScope.FULL : TaskReadScope.valueOf(entity.getReadScope());
    }

    private static List<TaskFocusRegionVO> focusRegions(TaskEntity entity) {
        List<TaskFocusRegionVO> regions = JsonUtils.parse(entity.getFocusRegionsJson(),
                new TypeReference<List<TaskFocusRegionVO>>() { });
        return regions == null ? List.of() : regions;
    }
}
