package com.agentdoc.common.feign.vo;

import java.time.LocalDateTime;

/**
 * Agent执行评估证据VO
 * <p>仅保存评估统计事实，不携带Prompt、入参、模型响应正文等大文本/敏感内容，用于评估指标计算与审计。</p>
 * @param taskId 任务ID
 * @param executionId Agent执行记录唯一ID
 * @param status 执行状态标识
 * @param traceId 链路追踪TraceId，关联OTel全链路日志
 * @param spanId 链路追踪SpanId
 * @param startedAt 执行开始时间
 * @param finishedAt 执行结束时间
 * @param toolCallCount 工具调用总次数
 * @param failedToolCallCount 工具调用失败次数
 * @param externalMcpCallCount 外部MCP能力调用次数
 */
public record AgentEvaluationEvidenceVO(
        Long taskId,
        Long executionId,
        String status,
        String traceId,
        String spanId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long toolCallCount,
        long failedToolCallCount,
        long externalMcpCallCount) {
}