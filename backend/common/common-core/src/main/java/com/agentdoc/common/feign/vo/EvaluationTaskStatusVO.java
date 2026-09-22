package com.agentdoc.common.feign.vo;

import java.time.LocalDateTime;

/** Replay Task 的最小状态投影。 */
public record EvaluationTaskStatusVO(
        Long taskId,
        Integer statusCode,
        String status,
        boolean terminal,
        Long executionId,
        String traceId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
}
