package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.execution.model.ModelAdapter;
import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.execution.model.ModelCapabilities;
import com.agentdoc.agent.execution.model.ModelTurnResult;
import com.agentdoc.agent.execution.model.TokenUsage;
import com.agentdoc.agent.execution.runtime.AgentExecutionCanceledException;
import com.agentdoc.agent.execution.runtime.AgentRuntimeResult;
import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.observability.AgentTelemetry;
import com.agentdoc.agent.pojo.entity.AgentExecutionModelCallEntity;
import com.agentdoc.agent.execution.audit.AgentExecutionModelCallAuditService;
import com.agentdoc.common.pojo.TokenValue;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderNeutralToolLoopTest {

    @Test
    void adapterConnectivityTestUsesOneModelTurn() {
        AtomicInteger calls = new AtomicInteger();
        ModelAdapter adapter = new ModelAdapter() {
            @Override
            public Set<ModelAdapterType> supportedTypes() {
                return Set.of(ModelAdapterType.OPENAI_CHAT);
            }

            @Override
            public ModelCapabilities capabilities() {
                return new ModelCapabilities(true, false);
            }

            @Override
            public ModelTurnResult callOnce(ModelAdapterContext context, List<Message> messages) {
                calls.incrementAndGet();
                return turn(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))),
                        "ok", 1L, 1L);
            }
        };

        adapter.testConnect(new ModelAdapterContext(null, null, "key", 20, List.of()));

        assertEquals(1, calls.get());
    }

    @Test
    void executesToolAndCallsModelAgain() {
        AtomicInteger toolCalls = new AtomicInteger();
        ToolCallback tool = tool("lookup", input -> {
            toolCalls.incrementAndGet();
            return "tool-result";
        });
        ModelAdapter adapter = sequenceAdapter();

        AgentRuntimeResult result = toolLoop().executeTrackingUsage(adapter,
                context(tool), "system", "question", 100L, 3, () -> false, ignored -> { });

        assertEquals("done", result.summary());
        assertEquals(1, toolCalls.get());
        assertEquals(3L, result.tokenUsage().input().value());
        assertEquals(4L, result.tokenUsage().output().value());
    }

    @Test
    void forwardsStreamingTextDeltasThroughToolLoop() {
        List<String> deltas = new ArrayList<>();
        ModelAdapter adapter = new ModelAdapter() {
            @Override
            public Set<ModelAdapterType> supportedTypes() {
                return Set.of(ModelAdapterType.OPENAI_CHAT);
            }

            @Override
            public ModelCapabilities capabilities() {
                return new ModelCapabilities(true, false);
            }

            @Override
            public ModelTurnResult callOnce(ModelAdapterContext context, List<Message> messages) {
                throw new AssertionError("流式执行不应回退到 callOnce");
            }

            @Override
            public ModelTurnResult stream(ModelAdapterContext context, List<Message> messages,
                                          Consumer<String> onTextDelta) {
                onTextDelta.accept("hel");
                onTextDelta.accept("lo");
                return turn(new ChatResponse(List.of(new Generation(new AssistantMessage("hello")))),
                        "hello", 1L, 1L);
            }
        };

        AgentRuntimeResult result = toolLoop().executeTrackingUsage(adapter,
                new ModelAdapterContext(null, null, "key", 20, List.of()),
                "system", "question", 100L, 3, () -> false, deltas::add, ignored -> { });

        assertEquals(List.of("hel", "lo"), deltas);
        assertEquals("hello", result.summary());
    }

    @Test
    void stopsBeforeExecutingToolWhenIterationLimitReached() {
        ToolCallback tool = tool("lookup", input -> "tool-result");

        assertThrows(IllegalStateException.class, () -> toolLoop().executeTrackingUsage(
                toolOnlyAdapter(), context(tool), "system", "question", 100L, 0, () -> false,
                ignored -> { }));
    }

    @Test
    void checksCancellationBeforeToolExecution() {
        ToolCallback tool = tool("lookup", input -> "tool-result");
        ToolCallback guardedTool = new CancellationAwareToolCallback(tool, () -> true);

        assertThrows(AgentExecutionCanceledException.class, () -> toolLoop().executeTrackingUsage(
                toolOnlyAdapter(), context(guardedTool), "system", "question", 100L, 3, () -> false,
                ignored -> { }));
    }

    @Test
    void reportsAccumulatedUsageWhenLaterModelCallFails() {
        AtomicReference<TokenUsage> latestUsage = new AtomicReference<>();
        ModelAdapter adapter = new ModelAdapter() {
            private int calls;

            @Override
            public Set<ModelAdapterType> supportedTypes() {
                return Set.of(ModelAdapterType.OPENAI_CHAT);
            }

            @Override
            public ModelCapabilities capabilities() {
                return new ModelCapabilities(true, false);
            }

            @Override
            public ModelTurnResult callOnce(ModelAdapterContext context, List<Message> messages) {
                if (calls++ == 0) {
                    AssistantMessage toolCall = AssistantMessage.builder()
                            .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function",
                                    "lookup", "{}")))
                            .build();
                    return turn(new ChatResponse(List.of(new Generation(toolCall))), null, 2L, 3L);
                }
                throw new IllegalStateException("second call failed");
            }
        };

        assertThrows(IllegalStateException.class, () -> toolLoop().executeTrackingUsage(
                adapter, context(tool("lookup", input -> "tool-result")), "system", "question",
                100L, 3, () -> false, latestUsage::set));

        assertEquals(2L, latestUsage.get().input().value());
        assertEquals(3L, latestUsage.get().output().value());
    }

    @Test
    void reportsUsageBeforeCancellationAfterModelTurn() {
        AtomicReference<TokenUsage> latestUsage = new AtomicReference<>();
        AtomicInteger cancellationChecks = new AtomicInteger();

        assertThrows(AgentExecutionCanceledException.class, () -> toolLoop().executeTrackingUsage(
                toolOnlyAdapter(), context(tool("lookup", input -> "tool-result")), "system", "question",
                100L, 3, () -> cancellationChecks.incrementAndGet() >= 2, latestUsage::set));

        assertEquals(1L, latestUsage.get().input().value());
        assertEquals(1L, latestUsage.get().output().value());
    }

    @Test
    void reportsUsageBeforeRejectingExceededBudget() {
        AtomicReference<TokenUsage> latestUsage = new AtomicReference<>();

        assertThrows(IllegalStateException.class, () -> toolLoop().executeTrackingUsage(
                toolOnlyAdapter(), context(tool("lookup", input -> "tool-result")), "system", "question",
                1L, 3, () -> false, latestUsage::set));

        assertEquals(1L, latestUsage.get().input().value());
        assertEquals(1L, latestUsage.get().output().value());
    }

    private ModelAdapter sequenceAdapter() {
        return new ModelAdapter() {
            private int calls;

            @Override
            public Set<ModelAdapterType> supportedTypes() {
                return Set.of(ModelAdapterType.OPENAI_CHAT);
            }

            @Override
            public ModelCapabilities capabilities() {
                return new ModelCapabilities(true, false);
            }

            @Override
            public ModelTurnResult callOnce(ModelAdapterContext context, List<Message> messages) {
                if (calls++ == 0) {
                    AssistantMessage toolCall = AssistantMessage.builder()
                            .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function",
                                    "lookup", "{}")))
                            .build();
                    return turn(new ChatResponse(List.of(new Generation(toolCall))), null, 1L, 1L);
                }
                return turn(new ChatResponse(List.of(new Generation(new AssistantMessage("done")))),
                        "done", 2L, 3L);
            }
        };
    }

    private ProviderNeutralToolLoop toolLoop() {
        AgentExecutionModelCallAuditService auditService = mock(AgentExecutionModelCallAuditService.class);
        when(auditService.start(any(), anyInt(), any(), anyBoolean())).thenAnswer(invocation -> {
            AgentExecutionModelCallEntity entity = new AgentExecutionModelCallEntity();
            entity.setSequenceNo(invocation.getArgument(1));
            return entity;
        });
        return new ProviderNeutralToolLoop(new TokenUsageEstimator(), auditService, new AgentTelemetry());
    }

    private ModelAdapter toolOnlyAdapter() {
        return new ModelAdapter() {
            @Override
            public Set<ModelAdapterType> supportedTypes() {
                return Set.of(ModelAdapterType.OPENAI_CHAT);
            }

            @Override
            public ModelCapabilities capabilities() {
                return new ModelCapabilities(true, false);
            }

            @Override
            public ModelTurnResult callOnce(ModelAdapterContext context, List<Message> messages) {
                AssistantMessage toolCall = AssistantMessage.builder()
                        .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function",
                                "lookup", "{}")))
                        .build();
                return turn(new ChatResponse(List.of(new Generation(toolCall))), null, 1L, 1L);
            }
        };
    }

    private ModelAdapterContext context(ToolCallback tool) {
        return new ModelAdapterContext(null, null, "key", 20, List.of(tool));
    }

    private ModelTurnResult turn(ChatResponse response, String text, long inputTokens, long outputTokens) {
        return new ModelTurnResult(response, text,
                new TokenUsage(TokenValue.provider(inputTokens), TokenValue.unavailable(),
                        TokenValue.provider(outputTokens)));
    }

    private ToolCallback tool(String name, Function<String, String> function) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
            }

            @Override
            public String call(String input) {
                return function.apply(input);
            }
        };
    }
}
