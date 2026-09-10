package com.agentdoc.document.pojo.vo;

import com.agentdoc.document.enums.DocumentVersionActorType;
import com.agentdoc.document.enums.DocumentVersionSourceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文档版本视图对象（不含正文快照，用于版本列表）。
 */
@Schema(description = "文档版本信息")
public record DocumentVersionVO(

        @Schema(description = "版本记录 ID")
        Long id,

        @Schema(description = "文档 ID")
        Long documentId,

        @Schema(description = "版本号（从 1 递增）")
        Long versionNo,

        @Schema(description = "变更摘要")
        String changeSummary,

        @Schema(description = "版本来源")
        DocumentVersionSourceType sourceType,

        @Schema(description = "版本操作主体类型")
        DocumentVersionActorType actorType,

        @Schema(description = "版本操作主体 ID")
        Long actorId,

        @Schema(description = "版本操作主体名称")
        String actorName,

        @Schema(description = "兼容字段：原版本创建主体 ID")
        Long createdBy,

        @Schema(description = "审批合并来源变更请求 ID")
        Long sourceChangeRequestId,

        @Schema(description = "来源任务 ID")
        Long sourceTaskId,

        @Schema(description = "任务编号")
        String taskNo,

        @Schema(description = "任务名称")
        String taskName,

        @Schema(description = "来源 Agent ID")
        Long agentId,

        @Schema(description = "来源 Agent 名称")
        String agentName,

        @Schema(description = "任务触发人用户 ID")
        Long triggeredBy,

        @Schema(description = "任务触发人名称")
        String triggeredByName,

        @Schema(description = "任务 Token 消耗")
        Long tokensUsed,

        @Schema(description = "Token 消耗是否包含估算值")
        Boolean tokensEstimated,

        @Schema(description = "审批人用户 ID")
        Long reviewedBy,

        @Schema(description = "审批人名称")
        String reviewedByName,

        @Schema(description = "审批时间")
        LocalDateTime reviewedAt,

        @Schema(description = "合并人用户 ID")
        Long mergedBy,

        @Schema(description = "合并人名称")
        String mergedByName,

        @Schema(description = "合并时间")
        LocalDateTime mergedAt,

        @Schema(description = "回滚来源版本号")
        Long rollbackFromVersion,

        @Schema(description = "正文快照 SHA-256")
        String contentSha256,

        @Schema(description = "是否存在可查看的任务执行快照")
        Boolean executionAvailable,

        @Schema(description = "创建时间")
        LocalDateTime createdAt
) {
}
