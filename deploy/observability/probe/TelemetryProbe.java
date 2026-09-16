import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/** OpenTelemetry Java Agent 与应用 OTel API 的最小兼容探针。 */
public class TelemetryProbe {

    public static void main(String[] args) throws InterruptedException {
        Tracer tracer = GlobalOpenTelemetry.getTracer("com.agentdoc.probe");
        Span span = tracer.spanBuilder("agentdoc.javaagent.probe").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("run.id", 9002L);
            span.setAttribute("agentdoc.tool.technical_name", "javaagent-probe");
            span.setAttribute("gen_ai.prompt", "ADWB_FORBIDDEN_JAVA_PROMPT");
            System.out.println(span.getSpanContext().getTraceId());
        } finally {
            span.end();
        }
        Thread.sleep(1500L);
    }
}
