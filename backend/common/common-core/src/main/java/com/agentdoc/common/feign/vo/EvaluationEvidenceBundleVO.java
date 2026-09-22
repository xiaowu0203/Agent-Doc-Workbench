package com.agentdoc.common.feign.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 六类确定性 Evaluator 共用的最小权威事实聚合。
 * <p>
 * 聚合单条评估任务所需的基础运行时证据，为各类评估器提供统一输入，
 * 不含大体积原始报文/文档正文，依靠哈希引用做一致性校验与回放。
 *
 * @param taskId                 工作台任务ID
 * @param spaceId                工作空间ID，权限隔离边界
 * @param taskStatusCode         任务状态编码
 * @param taskStatus             任务状态枚举文本
 * @param resultSummary          任务执行结果摘要文本
 * @param executionId            Agent执行记录ID
 * @param executionStatus       执行会话状态
 * @param traceId                全链路追踪TraceID，用于排查与链路关联
 * @param spanId                 当前段SpanID
 * @param startedAt              执行开始时间
 * @param finishedAt             执行结束时间
 * @param inputTokens            输入Token总量
 * @param cachedInputTokens      缓存命中的输入Token数量
 * @param outputTokens           输出Token总量
 * @param cost                   本次执行费用
 * @param currency               计费币种
 * @param retryCount             执行重试次数
 * @param toolCallCount          总工具调用次数
 * @param failedToolCallCount    失败工具调用次数
 * @param externalMcpCallCount  外部MCP服务调用次数
 * @param changeRequestCount     产生的变更请求数量
 * @param artifacts              隔离执行产物元数据列表（仅哈希与序号，不含payload）
 */
public record EvaluationEvidenceBundleVO(
        Long taskId,
        Long spaceId,
        Integer taskStatusCode,
        String taskStatus,
        String resultSummary,
        Long executionId,
        String executionStatus,
        String traceId,
        String spanId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long inputTokens,
        Long cachedInputTokens,
        Long outputTokens,
        BigDecimal cost,
        String currency,
        Integer retryCount,
        long toolCallCount,
        long failedToolCallCount,
        long externalMcpCallCount,
        long changeRequestCount,
        List<EvaluationArtifactEvidenceVO> artifacts) {
}
