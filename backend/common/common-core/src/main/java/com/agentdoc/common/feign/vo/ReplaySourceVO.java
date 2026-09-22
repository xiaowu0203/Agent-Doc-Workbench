package com.agentdoc.common.feign.vo;

/**
 * 可用于评估用例冻结的 Replay 来源投影。
 */
public record ReplaySourceVO(
        boolean replayable,
        String reasonCode,
        Long sourceTaskId,
        Long sourceExecutionId,
        Long spaceId,
        Long rootTaskId,
        String sourceLineage,
        Integer replayDepth,
        Integer inputSnapshotSchemaVersion,
        String inputSnapshotHash,
        Integer executionSnapshotSchemaVersion,
        String executionSnapshotHash,
        Long documentVersionSnapshot,
        String documentContentSha256) {
}
