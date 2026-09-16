package com.agentdoc.common.context;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String SPAN_ID = "0123456789abcdef";

    @AfterEach
    void clear() {
        TraceContext.clear();
    }

    @Test
    void prefersCurrentOpenTelemetryContext() {
        TraceContext.set("legacy-trace-id");
        Span span = Span.wrap(SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));

        try (Scope ignored = span.makeCurrent()) {
            assertThat(TraceContext.get()).isEqualTo(TRACE_ID);
            assertThat(TraceContext.getTelemetryTraceId()).isEqualTo(TRACE_ID);
            assertThat(TraceContext.getTelemetrySpanId()).isEqualTo(SPAN_ID);
        }
    }

    @Test
    void keepsValidIdentifiersWhenCurrentSpanIsNotSampled() {
        Span span = Span.wrap(SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault()));

        try (Scope ignored = span.makeCurrent()) {
            assertThat(TraceContext.getTelemetryTraceId()).isEqualTo(TRACE_ID);
            assertThat(TraceContext.getTelemetrySpanId()).isEqualTo(SPAN_ID);
        }
    }

    @Test
    void keepsLegacyValueOnlyAsCompatibilityFallback() {
        TraceContext.set("legacy-trace-id");

        assertThat(TraceContext.get()).isEqualTo("legacy-trace-id");
        assertThat(TraceContext.getTelemetryTraceId()).isNull();
        assertThat(TraceContext.getTelemetrySpanId()).isNull();
    }
}
