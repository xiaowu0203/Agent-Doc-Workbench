package com.agentdoc.agent.pojo.param;

import io.swagger.v3.oas.annotations.media.Schema;
import org.a2aproject.sdk.spec.TaskState;

import java.time.Instant;

/**
 * A2A 任务列表查询参数。
 */
@Schema(description = "A2A 任务列表查询参数")
public record A2aTaskSearchParam(
        @Schema(description = "A2A 上下文 ID")
        String contextId,

        @Schema(description = "任务状态")
        TaskState status,

        @Schema(description = "每页任务数量")
        Integer pageSize,

        @Schema(description = "分页游标")
        String pageToken,

        @Schema(description = "返回的历史消息数量")
        Integer historyLength,

        @Schema(description = "仅返回该时间之后状态发生变化的任务")
        Instant statusTimestampAfter,

        @Schema(description = "是否包含任务产物")
        Boolean includeArtifacts) {
}
