package com.agentdoc.common.context;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * TraceId 兼容上下文。优先读取 OpenTelemetry 当前 Context；ThreadLocal 只服务于 OTel 关闭时的日志兼容。
 */
public final class TraceContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId) {
        HOLDER.set(traceId);
    }

    public static String get() {
        String telemetryTraceId = getTelemetryTraceId();
        return telemetryTraceId == null ? HOLDER.get() : telemetryTraceId;
    }

    /**
     * 读取当前 OpenTelemetry Trace ID。
     *
     * @return 32 位小写十六进制 Trace ID；未启用 SDK 或当前无有效 Span 时返回 {@code null}
     */
    public static String getTelemetryTraceId() {
        SpanContext spanContext = Span.current().getSpanContext();
        return spanContext.isValid() ? spanContext.getTraceId() : null;
    }

    /**
     * 读取当前 OpenTelemetry Span ID。
     *
     * @return 16 位小写十六进制 Span ID；未启用 SDK 或当前无有效 Span 时返回 {@code null}
     */
    public static String getTelemetrySpanId() {
        SpanContext spanContext = Span.current().getSpanContext();
        return spanContext.isValid() ? spanContext.getSpanId() : null;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
