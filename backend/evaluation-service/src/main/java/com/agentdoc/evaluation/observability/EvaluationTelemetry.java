package com.agentdoc.evaluation.observability;

import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Evaluation 领域遥测入口，只记录稳定业务身份和状态，不记录正文、规则载荷、产物或凭证。
 */
@Component
public class EvaluationTelemetry {
    private static final String INSTRUMENTATION_SCOPE = "com.agentdoc.evaluation";
    private static final String EVALUATOR_SPAN_NAME = "agentdoc.evaluation.evaluator.execute";
    private static final int MAX_RULE_KEY_LENGTH = 128;

    private final Tracer tracer;

    public EvaluationTelemetry() {
        this(GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE));
    }

    EvaluationTelemetry(Tracer tracer) {
        this.tracer = tracer;
    }

    /** 创建一次确定性 Evaluator 执行 Span。 */
    public EvaluationSpan startEvaluator(Long runId, Long caseRunId, Long attemptId,
                                         Long evaluatorVersionId, String evaluatorKey) {
        var builder = tracer.spanBuilder(EVALUATOR_SPAN_NAME)
                .setAttribute("evaluation.run.id", runId)
                .setAttribute("evaluation.case_run.id", caseRunId)
                .setAttribute("evaluation.case_attempt.id", attemptId)
                .setAttribute("evaluation.evaluator_version.id", evaluatorVersionId);
        if (evaluatorKey != null && !evaluatorKey.isBlank() && evaluatorKey.length() <= MAX_RULE_KEY_LENGTH) {
            builder.setAttribute("evaluation.evaluator.key", evaluatorKey);
        }
        return new EvaluationSpan(builder.startSpan());
    }

    /** 显式管理 Span 生命周期，并向结果记录暴露当前 trace/span 身份。 */
    public static final class EvaluationSpan {
        private final Span span;
        private final AtomicBoolean ended = new AtomicBoolean();

        private EvaluationSpan(Span span) {
            this.span = span;
        }

        public Scope makeCurrent() {
            return span.makeCurrent();
        }

        public String traceId() {
            return span.getSpanContext().isValid() ? span.getSpanContext().getTraceId() : null;
        }

        public String spanId() {
            return span.getSpanContext().isValid() ? span.getSpanContext().getSpanId() : null;
        }

        public void complete(EvaluationResultStatus status) {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            span.setAttribute("evaluation.result.status", status.name());
            if (status == EvaluationResultStatus.ERROR) {
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
        }

        public void fail(Throwable exception) {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            span.setAttribute("evaluation.result.status", EvaluationResultStatus.ERROR.name());
            span.setAttribute("error.type", exception.getClass().getName());
            span.setStatus(StatusCode.ERROR);
            span.end();
        }
    }
}
