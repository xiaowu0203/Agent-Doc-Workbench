package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务执行血缘类型，表示本次执行为什么产生。
 */
@Schema(description = "任务执行血缘类型")
public enum TaskLineageType {

    ORIGINAL,
    RERUN,
    REVIEW_REWORK,
    REPLAY,
    EXPERIMENT,
    LEGACY_UNKNOWN
}
