package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务允许读取目标文档的范围。
 */
@Schema(description = "任务文档读取范围")
public enum TaskReadScope {

    /** 允许按需读取整个目标文档。 */
    FULL,

    /** 只允许读取创建任务时指定的多个字符区域。 */
    RANGES
}
