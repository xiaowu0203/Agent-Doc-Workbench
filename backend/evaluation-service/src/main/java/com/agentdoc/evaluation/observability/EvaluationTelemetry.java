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
 * <p>
 * 封装评估器执行的 Span 创建、上下文绑定、完成/异常终止逻辑；
 * 对外暴露 traceId/spanId，用于写入评估结果库，实现日志、存储、链路追踪三方关联。
 */
@Component
public class EvaluationTelemetry {

    /** 检测域标识，用于在 OTel 后端区分本服务埋点 */
    private static final String INSTRUMENTATION_SCOPE = "com.agentdoc.evaluation";
    /** Evaluator 执行 span 名称，统一链路检索关键字 */
    private static final String EVALUATOR_SPAN_NAME = "agentdoc.evaluation.evaluator.execute";
    /** evaluatorKey 属性最大长度，防止超长字符串污染遥测数据 */
    private static final int MAX_RULE_KEY_LENGTH = 128;

    private final Tracer tracer;

    /**
     * 默认构造，从全局 OTel 实例获取 Tracer。
     */
    public EvaluationTelemetry() {
        this(GlobalOpenTelemetry.getTracer(INSTRUMENTATION_SCOPE));
    }

    /**
     * 可注入构造，便于单元测试 mock Tracer。
     *
     * @param tracer OTel Tracer 实例
     */
    EvaluationTelemetry(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * 创建一次确定性 Evaluator 执行 Span。
     * <p>
     * 仅写入业务主键与评估器标识，不携带任何大体积载荷、文档正文或密钥。
     * 返回 {@link EvaluationSpan} 手动管理生命周期，不依赖 try-with-resources 自动 close。
     *
     * @param runId              评估运行实例ID
     * @param caseRunId          用例运行ID
     * @param attemptId          用例单次尝试ID
     * @param evaluatorVersionId 评估器版本ID
     * @param evaluatorKey       评估器业务标识key，超长时丢弃避免遥测超限
     * @return 封装后的 span 句柄
     */
    public EvaluationSpan startEvaluator(Long runId,
                                         Long caseRunId,
                                         Long attemptId,
                                         Long evaluatorVersionId,
                                         String evaluatorKey) {
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

    /**
     * 显式管理 Span 生命周期，并向结果记录暴露当前 trace/span 身份。
     * <p>
     * 线程安全：使用 AtomicBoolean 保证 span 只会 end 一次，防止重复结束抛异常。
     */
    public static final class EvaluationSpan {
        private final Span span;
        private final AtomicBoolean ended = new AtomicBoolean();

        private EvaluationSpan(Span span) {
            this.span = span;
        }

        /**
         * 将当前 span 设置为当前上下文 Scope，执行体内部会继承该 trace。
         * <p>
         * 调用方必须在合适时机调用 {@link Scope#close()}，一般放在 try‑with‑resources。
         *
         * @return 可关闭的上下文 Scope
         */
        public Scope makeCurrent() {
            return span.makeCurrent();
        }

        /**
         * 获取 traceId，用于写入评估结果数据库做链路关联。
         *
         * @return traceId；上下文无效时返回 null
         */
        public String traceId() {
            return span.getSpanContext().isValid() ? span.getSpanContext().getTraceId() : null;
        }

        /**
         * 获取 spanId，用于写入评估结果数据库做链路关联。
         *
         * @return spanId；上下文无效时返回 null
         */
        public String spanId() {
            return span.getSpanContext().isValid() ? span.getSpanContext().getSpanId() : null;
        }

        /**
         * 正常结束 span，带上评估结果状态。幂等，多次调用无副作用。
         *
         * @param status 评估结果枚举状态
         */
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

        /**
         * 异常终止 span，记录异常类型并标记为 ERROR。幂等。
         *
         * @param exception 执行抛出的异常
         */
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