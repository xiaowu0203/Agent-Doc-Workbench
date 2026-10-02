package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.enums.TaskTraceAvailability;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Task 授权范围内的遥测投影；微秒时间来自 Jaeger，不能作为账本或业务终态。 */
public record TaskTraceViewVO(
        @Schema(description = "Task/Run ID") Long taskId,
        @Schema(description = "所属 Space ID") Long spaceId,
        @Schema(description = "数据库中的 Trace ID") String traceId,
        @Schema(description = "遥测可用性") TaskTraceAvailability availabilityCode,
        @Schema(description = "投影可能不完整") boolean partial,
        @Schema(description = "节点因安全上限截断") boolean truncated,
        @Schema(description = "Unix 开始时间微秒；未知为空") Long startTimeMicros,
        @Schema(description = "Unix 结束时间微秒；未知为空") Long endTimeMicros,
        @Schema(description = "总耗时微秒；未知为空") Long durationMicros,
        @Schema(description = "本 Task 可见总 Span 数；不是返回节点数；未知为空") Integer spanCount,
        @Schema(description = "错误 Span 数；未知为空") Integer errorCount,
        @Schema(description = "取消 Span 数；未知为空") Integer canceledCount,
        @Schema(description = "可观测重试次数；未知为空") Long retryCount,
        @Schema(description = "本 Task 服务分布") List<ServiceSummaryVO> services,
        @Schema(description = "调用树/瀑布节点，不含其他 Task 分支") List<SpanNodeVO> spans
) {
    public record ServiceSummaryVO(
            @Schema(description = "受控服务名") String service,
            @Schema(description = "Span 数") int spanCount,
            @Schema(description = "错误数") int errorCount,
            @Schema(description = "累计 Span 耗时微秒，可能重叠，不等于独占耗时") long totalDurationMicros
    ) { }

    public record SpanNodeVO(
            @Schema(description = "Span ID") String spanId,
            @Schema(description = "父 Span ID，可空") String parentSpanId,
            @Schema(description = "受控稳定名称，不使用动态 SQL/URL Span 名") String name,
            @Schema(description = "受控服务名") String service,
            @Schema(description = "领域分类") String category,
            @Schema(description = "Span kind") String kind,
            @Schema(description = "受控状态，不代表 Task 终态") String status,
            @Schema(description = "Unix 开始时间微秒") long startTimeMicros,
            @Schema(description = "耗时微秒") long durationMicros,
            @Schema(description = "诊断白名单属性") List<AttributeVO> attributes
    ) { }

    public record AttributeVO(
            @Schema(description = "白名单属性名") String key,
            @Schema(description = "STRING/LONG/BOOLEAN") String valueType,
            @Schema(description = "规范化标量显示值，不含原始 payload") String value
    ) { }
}
