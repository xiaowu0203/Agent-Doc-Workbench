package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务执行血缘类型，表示本次执行为什么产生。
 */
@Schema(description = "任务执行血缘类型")
public enum TaskLineageType {
    // 原始
    ORIGINAL,
    // 重新运行（异常后重试）
    RERUN,
    // 重新审核（审批不通过，重试）
    REVIEW_REWORK,
    // 重播
    REPLAY,
    // 实验
    EXPERIMENT,
    // 未知
    LEGACY_UNKNOWN
}
