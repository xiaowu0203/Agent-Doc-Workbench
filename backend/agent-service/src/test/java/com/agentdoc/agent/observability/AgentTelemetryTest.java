package com.agentdoc.agent.observability;

import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.execution.skill.SkillSelectionResult;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.pojo.TokenValue;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTelemetryTest {

    private final Tracer tracer = mock(Tracer.class);
    private final SpanBuilder spanBuilder = mock(SpanBuilder.class);
    private final Span span = mock(Span.class);
    private AgentTelemetry telemetry;

    @BeforeEach
    void setUp() {
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.setSpanKind(any(SpanKind.class))).thenReturn(spanBuilder);
        when(spanBuilder.setAttribute(anyString(), anyString())).thenReturn(spanBuilder);
        when(spanBuilder.setAttribute(anyString(), anyLong())).thenReturn(spanBuilder);
        when(spanBuilder.setAttribute(anyString(), anyBoolean())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        telemetry = new AgentTelemetry(tracer);
    }

    @Test
    void recordsApprovedModelAttributesAndActualUsage() {
        AgentTelemetry.ModelCallSpan call = telemetry.startModelCall(
                context("deepseek", "deepseek-chat"), 2, false, "CUSTOM");

        call.succeed(new TokenUsage(TokenValue.provider(12L), TokenValue.unavailable(),
                TokenValue.provider(7L)));

        verify(tracer).spanBuilder("gen_ai.client.operation");
        verify(spanBuilder).setSpanKind(SpanKind.CLIENT);
        verify(spanBuilder).setAttribute("gen_ai.operation.name", "chat");
        verify(spanBuilder).setAttribute("gen_ai.provider.name", "deepseek");
        verify(spanBuilder).setAttribute("gen_ai.request.model", "deepseek-chat");
        verify(spanBuilder).setAttribute("agentdoc.model.call.sequence", 2L);
        verify(spanBuilder).setAttribute("agentdoc.model.stream", false);
        verify(spanBuilder).setAttribute("agentdoc.runtime.type", "CUSTOM");
        verify(spanBuilder).setAttribute("execution.id", 91L);
        verify(span).setAttribute("gen_ai.usage.input_tokens", 12L);
        verify(span).setAttribute("gen_ai.usage.output_tokens", 7L);
        verify(span).setAttribute("agentdoc.model.usage.source", "ACTUAL");
        verify(span).setAttribute("agentdoc.operation.status", "completed");
        verify(span).end();
    }

    @Test
    void recordsOnlyControlledErrorTypeAndEndsOnce() {
        AgentTelemetry.ModelCallSpan call = telemetry.startModelCall(
                context("deepseek", "deepseek-chat"), 1, true, "SPRING_AI_ALIBABA");
        IllegalStateException failure = new IllegalStateException("sensitive provider response");

        call.fail(failure);
        call.fail(failure);

        verify(span).setAttribute("error.type", IllegalStateException.class.getName());
        verify(span).setStatus(StatusCode.ERROR);
        verify(span, never()).setAttribute("exception.message", failure.getMessage());
        verify(span, times(1)).end();
    }

    @Test
    void distinguishesExternalMcpToolFromSkillRead() {
        AgentTelemetry.ToolCallSpan toolCall = telemetry.startToolCall(
                91L, "search_docs", "MCP_REMOTE", 17L, null);
        toolCall.succeed(42L);

        verify(tracer).spanBuilder("agentdoc.mcp.tool.execute");
        verify(spanBuilder).setAttribute("agentdoc.tool.source", "EXTERNAL_MCP");
        verify(spanBuilder).setAttribute("agentdoc.tool.technical_name", "search_docs");
        verify(spanBuilder).setAttribute("agentdoc.mcp.server_id", 17L);
        verify(span).setAttribute("agentdoc.tool.result_size_bytes", 42L);

        AgentTelemetry.ToolCallSpan skillRead = telemetry.startToolCall(
                91L, "skill_resource_read", "SKILL_LOCAL", null, 23L);
        skillRead.fail("INVALID_TOOL_ARGUMENTS_JSON");

        verify(tracer).spanBuilder("agentdoc.skill.read");
        verify(spanBuilder).setAttribute("agentdoc.skill.version_id", 23L);
    }

    @Test
    void recordsOnlyCountsForSkillSelection() {
        SkillSelectionResult result = new SkillSelectionResult("ALL_BOUND", List.of(), null);

        telemetry.selectSkills("ALL_BOUND", 3, () -> result);

        verify(tracer).spanBuilder("agentdoc.skill.select");
        verify(spanBuilder).setAttribute("agentdoc.skill.selection_mode", "ALL_BOUND");
        verify(spanBuilder).setAttribute("agentdoc.skill.candidate_count", 3L);
        verify(span).setAttribute("agentdoc.skill.selection_mode", "ALL_BOUND");
        verify(span).setAttribute("agentdoc.skill.selected_count", 0L);
        verify(span).setAttribute("agentdoc.operation.status", "completed");
        verify(span).end();
    }

    @Test
    void recordsMcpConnectionWithoutEndpointOrCredentials() {
        telemetry.connectMcp(91L, 17L, () -> "connected");

        verify(tracer).spanBuilder("agentdoc.mcp.connect");
        verify(spanBuilder).setAttribute("execution.id", 91L);
        verify(spanBuilder).setAttribute("agentdoc.mcp.server_id", 17L);
        verify(spanBuilder).setAttribute("agentdoc.mcp.external", true);
        verify(spanBuilder).setAttribute("agentdoc.tool.source", "EXTERNAL_MCP");
        verify(spanBuilder, never()).setAttribute("server.address", "https://secret.example");
        verify(span).setAttribute("agentdoc.operation.status", "completed");
        verify(span).end();
    }

    @Test
    void distinguishesWorkbenchMcpConnection() {
        telemetry.connectMcp(91L, null, () -> "connected");

        verify(tracer).spanBuilder("agentdoc.mcp.connect");
        verify(spanBuilder).setAttribute("agentdoc.mcp.external", false);
        verify(spanBuilder).setAttribute("agentdoc.tool.source", "WORKBENCH");
        verify(spanBuilder, never()).setAttribute("agentdoc.mcp.server_id", 17L);
        verify(span).setAttribute("agentdoc.operation.status", "completed");
    }

    private ModelAdapterContext context(String provider, String modelKey) {
        ModelEntity model = new ModelEntity();
        model.setProvider(provider);
        model.setModelKey(modelKey);
        return new ModelAdapterContext(null, model, "secret", 100, List.of()).withExecutionId(91L);
    }
}
