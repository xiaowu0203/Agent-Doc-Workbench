package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.TaskLineageType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Replay 准入结果")
public record ReplayEligibilityVO(
        boolean replayable,
        String reasonCode,
        Long sourceTaskId,
        Long sourceExecutionId,
        Long rootTaskId,
        TaskLineageType sourceLineage,
        Integer replayDepth,
        Integer inputSnapshotSchemaVersion,
        String inputSnapshotHash,
        Integer executionSnapshotSchemaVersion,
        String executionSnapshotHash) {
}
