package com.agentdoc.evaluation.pojo.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "脱敏线上分配投影；不自动读取正文/任务")
public record OnlineAssignmentVO(
        @Schema(description = "分配身份") String id,
        @Schema(description = "实验") String experimentId,
        @Schema(description = "空间") String spaceId,
        @Schema(description = "Agent") String agentId,
        @Schema(description = "预分配Task") String taskId,
        @Schema(description = "文档") String documentId,
        @Schema(description = "发起者") String createdBy,
        @Schema(description = "输入协议") Integer inputSnapshotSchemaVersion,
        @Schema(description = "输入摘要") String inputSnapshotHash,
        @Schema(description = "稳定桶") Integer bucket,
        @Schema(description = "稳定组") String variant,
        @Schema(description = "新模板身份") String templateId,
        @Schema(description = "模板协议") Integer configSchemaVersion,
        @Schema(description = "模板摘要") String configHash,
        @Schema(description = "依赖摘要") String dependencyHash,
        @Schema(description = "绑定协议") Integer bindingSchemaVersion,
        @Schema(description = "绑定摘要") String bindingHash,
        @Schema(description = "接受顺序") Long acceptedSequence,
        @Schema(description = "Token预留") String reservedTokenBudget,
        @Schema(description = "确认状态") String taskConfirmationStatus,
        @Schema(description = "派发状态") String dispatchStatus,
        @Schema(description = "占槽状态") String slotStatus,
        @Schema(description = "取消状态") String cancelStatus,
        @Schema(description = "结算状态") String settlementStatus,
        @Schema(description = "权威执行身份") String executionId,
        @Schema(description = "实际Token未知为空") String consumedTokens,
        @Schema(description = "未决原因") String reasonCode,
        @Schema(description = "确认时间") LocalDateTime taskConfirmedAt,
        @Schema(description = "结算时间") LocalDateTime settledAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "权威 Task/Execution 读取状态，P6-02 接入") String factStatus
) { }
