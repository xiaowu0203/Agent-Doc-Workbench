package com.agentdoc.common.feign.vo;

import java.time.LocalDateTime;

/**
 * Replay Task 的最小状态投影。
 * <p>
 * 用于Worker轮询查询回放任务状态，仅返回必要的状态、时间和链路信息，
 * 不携带评估证据、明细载荷等大数据，降低轮询开销。
 *
 * @param taskId        评估回放任务ID
 * @param statusCode    状态数字编码
 * @param status        状态枚举文本
 * @param terminal      是否为终态（完成/失败/取消，不再更新）
 * @param executionId   关联Agent执行记录ID，可为null
 * @param traceId       全链路追踪TraceID，用于问题排查
 * @param startedAt     任务开始时间
 * @param finishedAt    任务结束时间，未结束时为null
 */
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
