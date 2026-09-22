package com.agentdoc.evaluation.service;

import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.evaluation.config.EvaluationRuntimeProperties;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.observability.EvaluationRuntimeMetrics;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationReconciliationJobTest {

    @Mock private EvaluationRunMapper runMapper;
    @Mock private EvaluationRunProcessor processor;
    @Mock private RedisUtils redisUtils;
    @Mock private EvaluationRuntimeMetrics runtimeMetrics;

    @Test
    void disabledGateSkipsDatabaseAndWorker() {
        EvaluationRuntimeProperties properties = new EvaluationRuntimeProperties();
        properties.setReconciliationEnabled(false);
        EvaluationReconciliationJob job = new EvaluationReconciliationJob(
                runMapper, processor, properties, redisUtils, runtimeMetrics);

        job.reconcile();

        verify(runMapper, never()).selectList(any());
        verify(processor, never()).process(any());
    }

    @Test
    void recordsScanAndProcessesOnlyRunWhoseLeaseWasAcquired() {
        EvaluationRuntimeProperties properties = new EvaluationRuntimeProperties();
        properties.setReconciliationBatchSize(20);
        properties.setReconciliationLockSeconds(30);
        EvaluationRunEntity first = run(11L);
        EvaluationRunEntity second = run(12L);
        when(runMapper.selectList(any())).thenReturn(List.of(first, second));
        when(redisUtils.setIfAbsent(eq("evaluation:reconcile:run:11"), any(String.class),
                eq(Duration.ofSeconds(30)))).thenReturn(true);
        when(redisUtils.setIfAbsent(eq("evaluation:reconcile:run:12"), any(String.class),
                eq(Duration.ofSeconds(30)))).thenReturn(false);
        EvaluationReconciliationJob job = new EvaluationReconciliationJob(
                runMapper, processor, properties, redisUtils, runtimeMetrics);

        job.reconcile();

        verify(runtimeMetrics).recordReconciliationScan(2);
        verify(processor).process(11L);
        verify(processor, never()).process(12L);
        verify(runtimeMetrics).recordReconciliationProcessed();
        verify(runtimeMetrics).recordReconciliationLockContention();
        verify(redisUtils).deleteIfValueMatches(eq("evaluation:reconcile:run:11"), any(String.class));
    }

    private static EvaluationRunEntity run(Long id) {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(id);
        run.setStatus(EvaluationRunStatus.RUNNING.name());
        return run;
    }
}
