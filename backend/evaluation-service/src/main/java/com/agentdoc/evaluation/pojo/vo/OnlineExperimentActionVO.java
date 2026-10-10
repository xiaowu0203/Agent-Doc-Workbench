package com.agentdoc.evaluation.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 已提交动作的不可变结果，时间采用 ISO 本地时间，与现有详情投影一致。 */
public record OnlineExperimentActionVO(
        @Schema(description = "实验") String experimentId,
        @Schema(description = "动作提交后的状态") String status,
        @Schema(description = "动作提交后的状态版本") String stateVersion,
        @Schema(description = "冻结摘要") String manifestHash,
        @Schema(description = "原因") String reasonCode,
        @Schema(description = "空间占位") Integer activeSlot,
        @Schema(description = "首次开始时点") String startedAt,
        @Schema(description = "原分配截止") String assignmentDeadline,
        @Schema(description = "原观察截止") String observationDeadline,
        @Schema(description = "紧急关门时点") String emergencyStopRequestedAt) { }
