package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.TaskReadScope;
import com.agentdoc.task.pojo.entity.TaskDraftEntity;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务草稿视图。
 */
@Schema(description = "任务草稿")
public record TaskDraftVO(
        @Schema(description = "草稿 ID") Long id,
        @Schema(description = "所属空间 ID") Long spaceId,
        @Schema(description = "Agent ID") Long agentId,
        @Schema(description = "目标文档 ID") Long documentId,
        @Schema(description = "任务名称") String name,
        @Schema(description = "任务指令") String instruction,
        @Schema(description = "任务 Token 预算") Long tokenBudget,
        @Schema(description = "读取范围") TaskReadScope readScope,
        @Schema(description = "关注区域") List<TaskFocusRegionVO> focusRegions,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {

    public static TaskDraftVO from(TaskDraftEntity entity) {
        TaskReadScope scope = entity.getReadScope() == null
                ? TaskReadScope.FULL : TaskReadScope.valueOf(entity.getReadScope());
        return new TaskDraftVO(entity.getId(), entity.getSpaceId(), entity.getAgentId(), entity.getDocumentId(),
                entity.getName(), entity.getInstruction(), entity.getTokenBudget(), scope,
                focusRegions(entity), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static List<TaskFocusRegionVO> focusRegions(TaskDraftEntity entity) {
        List<TaskFocusRegionVO> regions = JsonUtils.parse(entity.getFocusRegionsJson(),
                new TypeReference<List<TaskFocusRegionVO>>() { });
        return regions == null ? List.of() : regions;
    }
}
