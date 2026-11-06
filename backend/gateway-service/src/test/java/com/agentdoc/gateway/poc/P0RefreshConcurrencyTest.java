package com.agentdoc.gateway.poc;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 单会话协调原型，正式Redis原子写入和Spring客户端集成仍需单独验证。 */
class P0RefreshConcurrencyTest {
    @Test
    void concurrentCallersShareOneRefresh() throws Exception {
        var coordinator = new Coordinator();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> coordinator.refresh(() -> {
                calls.incrementAndGet(); entered.countDown(); await(release); return "rotated";
            }, false));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> coordinator.refresh(() -> { calls.incrementAndGet(); return "unexpected"; }, false));
            assertThat(coordinator.waiting.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("rotated");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("rotated");
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void logoutDuringRefreshPreventsLateWrite() throws Exception {
        var coordinator = new Coordinator();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newSingleThreadExecutor()) {
            var result = workers.submit(() -> coordinator.refresh(() -> { entered.countDown(); await(release); return "rotated"; }, false));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            coordinator.logout();
            release.countDown();
            assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS)).hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(coordinator.saved).isNull();
            assertThat(coordinator.active).isFalse();
        }
    }

    @Test
    void failedPersistenceInvalidatesSessionAndDoesNotReuseOldToken() {
        var coordinator = new Coordinator();
        assertThatThrownBy(() -> coordinator.refresh(() -> "rotated", true)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(coordinator.active).isFalse();
        assertThat(coordinator.saved).isNull();
        assertThatThrownBy(() -> coordinator.refresh(() -> "reuse", false)).isInstanceOf(IllegalStateException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("timeout"); }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", error);
        }
    }

    private static final class Coordinator {
        private boolean active = true;
        private String saved;
        private CompletableFuture<String> inFlight;
        private final CountDownLatch waiting = new CountDownLatch(1);

        String refresh(Supplier<String> provider, boolean failSave) {
            CompletableFuture<String> future;
            boolean leader;
            synchronized (this) {
                if (!active) { throw new IllegalStateException("session invalid"); }
                leader = inFlight == null;
                if (leader) { inFlight = new CompletableFuture<>(); } else { waiting.countDown(); }
                future = inFlight;
            }
            if (leader) {
                try {
                    String rotated = provider.get();
                    synchronized (this) {
                        if (!active || failSave) { active = false; throw new IllegalStateException("session invalid"); }
                        saved = rotated;
                        future.complete(rotated);
                        inFlight = null;
                    }
                } catch (RuntimeException error) {
                    synchronized (this) { active = false; saved = null; future.completeExceptionally(error); inFlight = null; }
                }
            }
            return future.join();
        }

        synchronized void logout() { active = false; saved = null; }
    }
}
