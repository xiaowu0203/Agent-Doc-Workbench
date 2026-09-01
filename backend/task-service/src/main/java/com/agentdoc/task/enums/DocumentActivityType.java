package com.agentdoc.task.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文档活动来源类型。
 */
@Schema(description = "文档活动类型")
public enum DocumentActivityType {

    /** Agent 任务。 */
    TASK,

    /** 文档变更请求。 */
    CHANGE_REQUEST
}
