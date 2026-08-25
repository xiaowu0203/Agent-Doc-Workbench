package com.agentdoc.agent.execution.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelChatModelCacheTest {

    @Test
    void reusesModelForSameModelAndConfigVersion() {
        ModelChatModelCache cache = new ModelChatModelCache(2);
        AtomicInteger builds = new AtomicInteger();
        CloseableChatModel model = new CloseableChatModel();

        ChatModel first = cache.getOrCreate(1L, 3L, () -> {
            builds.incrementAndGet();
            return model;
        });
        ChatModel second = cache.getOrCreate(1L, 3L, () -> {
            builds.incrementAndGet();
            return new CloseableChatModel();
        });

        assertSame(first, second);
        assertEquals(1, builds.get());
        assertEquals(1, cache.size());
    }

    @Test
    void evictsLeastRecentlyUsedModelAndClosesIt() {
        ModelChatModelCache cache = new ModelChatModelCache(2);
        CloseableChatModel first = new CloseableChatModel();
        CloseableChatModel second = new CloseableChatModel();
        CloseableChatModel third = new CloseableChatModel();

        cache.getOrCreate(1L, 1L, () -> first);
        cache.getOrCreate(2L, 1L, () -> second);
        cache.getOrCreate(1L, 1L, () -> new CloseableChatModel());
        cache.getOrCreate(3L, 1L, () -> third);

        assertFalse(cache.contains(2L, 1L));
        assertTrue(cache.contains(1L, 1L));
        assertTrue(second.closed);
        assertEquals(2, cache.size());
    }

    @Test
    void invalidatesAllVersionsForModel() {
        ModelChatModelCache cache = new ModelChatModelCache(3);
        CloseableChatModel first = new CloseableChatModel();
        CloseableChatModel second = new CloseableChatModel();
        CloseableChatModel other = new CloseableChatModel();

        cache.getOrCreate(1L, 1L, () -> first);
        cache.getOrCreate(1L, 2L, () -> second);
        cache.getOrCreate(2L, 1L, () -> other);
        cache.invalidate(1L);

        assertFalse(cache.contains(1L, 1L));
        assertFalse(cache.contains(1L, 2L));
        assertTrue(cache.contains(2L, 1L));
        assertTrue(first.closed);
        assertTrue(second.closed);
        assertFalse(other.closed);
    }

    private static final class CloseableChatModel implements ChatModel, AutoCloseable {

        private boolean closed;

        @Override
        public ChatResponse call(Prompt prompt) {
            return null;
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return null;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
