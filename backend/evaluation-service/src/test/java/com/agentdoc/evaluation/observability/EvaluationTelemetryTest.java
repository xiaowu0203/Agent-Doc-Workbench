package com.agentdoc.evaluation.observability;

import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationTelemetryTest {

    @Test
    void recordsOnlyStableEvaluationIdentityAndResultStatus() {
        Tracer tracer = mock(Tracer.class);
        SpanBuilder builder = mock(SpanBuilder.class, RETURNS_SELF);
        Span span = mock(Span.class);
        SpanContext context = mock(SpanContext.class);
        when(tracer.spanBuilder("agentdoc.evaluation.evaluator.execute")).thenReturn(builder);
        when(builder.startSpan()).thenReturn(span);
        when(span.getSpanContext()).thenReturn(context);
        when(context.isValid()).thenReturn(true);
        when(context.getTraceId()).thenReturn("a".repeat(32));
        when(context.getSpanId()).thenReturn("b".repeat(16));

        EvaluationTelemetry.EvaluationSpan handle = new EvaluationTelemetry(tracer)
                .startEvaluator(71L, 72L, 73L, 41L, "artifact-contract");

        assertThat(handle.traceId()).isEqualTo("a".repeat(32));
        assertThat(handle.spanId()).isEqualTo("b".repeat(16));
        handle.complete(EvaluationResultStatus.PASSED);
        handle.complete(EvaluationResultStatus.PASSED);

        verify(builder).setAttribute("evaluation.run.id", 71L);
        verify(builder).setAttribute("evaluation.case_run.id", 72L);
        verify(builder).setAttribute("evaluation.case_attempt.id", 73L);
        verify(builder).setAttribute("evaluation.evaluator_version.id", 41L);
        verify(builder).setAttribute("evaluation.evaluator.key", "artifact-contract");
        verify(span).setAttribute("evaluation.result.status", EvaluationResultStatus.PASSED.name());
        verify(span, times(1)).end();
    }

    @Test
    void errorMarksSpanWithoutRecordingExceptionMessage() {
        Tracer tracer = mock(Tracer.class);
        SpanBuilder builder = mock(SpanBuilder.class, RETURNS_SELF);
        Span span = mock(Span.class, RETURNS_SELF);
        when(tracer.spanBuilder("agentdoc.evaluation.evaluator.execute")).thenReturn(builder);
        when(builder.startSpan()).thenReturn(span);

        EvaluationTelemetry.EvaluationSpan handle = new EvaluationTelemetry(tracer)
                .startEvaluator(71L, 72L, 73L, 41L, "document-change-validator");
        handle.fail(new IllegalStateException("sensitive details"));

        verify(span).setAttribute("evaluation.result.status", EvaluationResultStatus.ERROR.name());
        verify(span).setAttribute("error.type", IllegalStateException.class.getName());
        verify(span).setStatus(StatusCode.ERROR);
        verify(span).end();
    }
}
