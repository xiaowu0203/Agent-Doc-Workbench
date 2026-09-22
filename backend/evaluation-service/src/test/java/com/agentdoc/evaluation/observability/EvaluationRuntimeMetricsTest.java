package com.agentdoc.evaluation.observability;

import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationRuntimeMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ThreadPoolTaskExecutor executor = executor();

    @AfterEach
    void tearDown() {
        executor.shutdown();
        registry.close();
    }

    @Test
    void exposesOnlyOperationalWorkerAndEvaluatorMetrics() {
        EvaluationRuntimeMetrics metrics = new EvaluationRuntimeMetrics(registry, executor);

        metrics.recordRejected();
        metrics.recordTimeout();
        metrics.recordReconciliationFailure();
        metrics.recordReconciliationScan(2);
        metrics.recordReconciliationProcessed();
        metrics.recordReconciliationLockContention();
        metrics.recordEvaluatorDuration(System.nanoTime() - 1_000_000, EvaluationResultStatus.PASSED);

        assertThat(registry.get("agentdoc.evaluation.worker.active").gauge().value()).isZero();
        assertThat(registry.get("agentdoc.evaluation.worker.queue.depth").gauge().value()).isZero();
        assertThat(registry.get("agentdoc.evaluation.worker.rejected").counter().count()).isEqualTo(1);
        assertThat(registry.get("agentdoc.evaluation.evaluator.timeout").counter().count()).isEqualTo(1);
        assertThat(registry.get("agentdoc.evaluation.reconciliation.failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("agentdoc.evaluation.reconciliation.scanned").counter().count()).isEqualTo(2);
        assertThat(registry.get("agentdoc.evaluation.reconciliation.processed").counter().count()).isEqualTo(1);
        assertThat(registry.get("agentdoc.evaluation.reconciliation.lock.contention").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("agentdoc.evaluation.evaluator.duration")
                .tag("result_status", "passed").timer().count()).isEqualTo(1);
    }

    private static ThreadPoolTaskExecutor executor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();
        return executor;
    }
}
