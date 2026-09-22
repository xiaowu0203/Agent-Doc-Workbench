package com.agentdoc.evaluation.observability;

import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Evaluation Worker 的运行健康指标。此处的 Micrometer 指标不属于领域 Metric，
 */
@Component
public class EvaluationRuntimeMetrics {
    private static final String RESULT_STATUS_TAG = "result_status";

    private final MeterRegistry registry;
    private final Counter rejectedCounter;
    private final Counter timeoutCounter;
    private final Counter reconciliationFailureCounter;
    private final Counter reconciliationScanCounter;
    private final Counter reconciliationProcessedCounter;
    private final Counter reconciliationLockContentionCounter;

    public EvaluationRuntimeMetrics(MeterRegistry registry,
                                    @Qualifier("evaluationExecutor") ThreadPoolTaskExecutor executor) {
        this.registry = registry;
        this.rejectedCounter = registry.counter("agentdoc.evaluation.worker.rejected");
        this.timeoutCounter = registry.counter("agentdoc.evaluation.evaluator.timeout");
        this.reconciliationFailureCounter = registry.counter("agentdoc.evaluation.reconciliation.failure");
        this.reconciliationScanCounter = registry.counter("agentdoc.evaluation.reconciliation.scanned");
        this.reconciliationProcessedCounter = registry.counter("agentdoc.evaluation.reconciliation.processed");
        this.reconciliationLockContentionCounter = registry.counter(
                "agentdoc.evaluation.reconciliation.lock.contention");
        Gauge.builder("agentdoc.evaluation.worker.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .register(registry);
        Gauge.builder("agentdoc.evaluation.worker.queue.depth", executor,
                        value -> value.getThreadPoolExecutor().getQueue().size())
                .register(registry);
    }

    public void recordRejected() {
        rejectedCounter.increment();
    }

    public void recordTimeout() {
        timeoutCounter.increment();
    }

    public void recordReconciliationFailure() {
        reconciliationFailureCounter.increment();
    }

    public void recordReconciliationScan(int count) {
        if (count > 0) {
            reconciliationScanCounter.increment(count);
        }
    }

    public void recordReconciliationProcessed() {
        reconciliationProcessedCounter.increment();
    }

    public void recordReconciliationLockContention() {
        reconciliationLockContentionCounter.increment();
    }

    public void recordEvaluatorDuration(long startedNanos, EvaluationResultStatus status) {
        String tagValue = status == null ? "infrastructure_error" : status.name().toLowerCase();
        registry.timer("agentdoc.evaluation.evaluator.duration", RESULT_STATUS_TAG, tagValue)
                .record(Duration.ofNanos(System.nanoTime() - startedNanos));
    }
}
