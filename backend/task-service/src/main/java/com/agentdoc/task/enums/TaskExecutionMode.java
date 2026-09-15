package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务执行副作用模式。
 */
@Schema(description = "任务执行副作用模式")
public enum TaskExecutionMode {

    LIVE,
    ISOLATED
}
