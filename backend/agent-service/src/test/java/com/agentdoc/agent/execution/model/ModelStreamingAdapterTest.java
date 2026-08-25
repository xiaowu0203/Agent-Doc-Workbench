package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelStreamingAdapterTest {

    @Test
    void aggregatesTextAndFragmentedToolCallsFromStream() {
        ExposedAdapter adapter = new ExposedAdapter();
        List<String> deltas = new ArrayList<>();

        ModelTurnResult result = adapter.stream(context(), List.of(new UserMessage("question")), deltas::add);

        assertEquals(List.of("hel", "lo"), deltas);
        assertEquals("hello", result.text());
        assertEquals("{}", result.response().getResult().getOutput().getToolCalls().getFirst().arguments());
    }

    private ModelAdapterContext context() {
        ModelEntity model = new ModelEntity();
        model.setId(1L);
        model.setConfigVersion(1L);
        return new ModelAdapterContext(null, model, "key", 20, List.of());
    }

    private static final class ExposedAdapter extends AbstractSpringAiModelAdapter {

        private ExposedAdapter() {
            super(new ModelChatModelCache(2));
        }

        @Override
        public Set<ModelAdapterType> supportedTypes() {
            return Set.of(ModelAdapterType.OPENAI_CHAT);
        }

        @Override
        public ModelCapabilities capabilities() {
            return new ModelCapabilities(true, false);
        }

        @Override
        protected ChatModel chatModel(ModelAdapterContext context) {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ChatOptions getDefaultOptions() {
                    return null;
                }

                @Override
                public Flux<ChatResponse> stream(Prompt prompt) {
                    return Flux.just(
                            response(new AssistantMessage("hel")),
                            response(AssistantMessage.builder()
                                    .content("lo")
                                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                                            "call-1", "function", "lookup", "{")))
                                    .build()),
                            response(AssistantMessage.builder()
                                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                                            "call-1", "function", "lookup", "}")))
                                    .build()));
                }
            };
        }

        private ChatResponse response(AssistantMessage message) {
            return new ChatResponse(List.of(new Generation(message)));
        }
    }
}
