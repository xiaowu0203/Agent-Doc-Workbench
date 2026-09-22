package com.agentdoc.evaluation.service;

import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.evaluation.config.EvaluationRuntimeProperties;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.observability.EvaluationRuntimeMetrics;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 多实例安全的 EvaluationRun 定时推进入口。
 * 定时轮询处于 RUNNING / CANCEL_PENDING 状态的评估任务，通过Redis分布式锁保证多实例不会并发处理同一个Run。
 */
@Component
@RequiredArgsConstructor
public class EvaluationReconciliationJob {
    /**
     * 分布式锁key前缀
     */
    private static final String LOCK_PREFIX = "evaluation:reconcile:run:";

    private final EvaluationRunMapper runMapper;
    private final EvaluationRunProcessor processor;
    private final EvaluationRuntimeProperties properties;
    private final RedisUtils redisUtils;
    private final EvaluationRuntimeMetrics runtimeMetrics;

    /**
     * 定时对账任务
     * 按配置间隔轮询，查询待推进状态的Run，限制批量条数，逐个尝试加锁处理
     */
    @Scheduled(fixedDelayString = "${agent-doc.evaluation.runtime.reconciliation-interval-ms:5000}")
    public void reconcile() {
        if (!properties.isReconciliationEnabled()) {
            return;
        }
        int batchSize = Math.max(1, Math.min(properties.getReconciliationBatchSize(), 100));
        LocalDateTime staleDispatchBefore = LocalDateTime.now()
                .minusSeconds(Math.max(5, properties.getDispatchStaleSeconds()));
        List<EvaluationRunEntity> runs = runMapper.selectList(new LambdaQueryWrapper<EvaluationRunEntity>()
                .and(query -> query.in(EvaluationRunEntity::getStatus, List.of(EvaluationRunStatus.RUNNING.name(),
                                EvaluationRunStatus.CANCEL_PENDING.name()))
                        .or(nested -> nested.eq(EvaluationRunEntity::getStatus,
                                        EvaluationRunStatus.DISPATCHING.name())
                                .lt(EvaluationRunEntity::getUpdatedAt, staleDispatchBefore)))
                .orderByAsc(EvaluationRunEntity::getUpdatedAt, EvaluationRunEntity::getId)
                .last("LIMIT " + batchSize));
        runtimeMetrics.recordReconciliationScan(runs.size());
        for (EvaluationRunEntity run : runs) {
            processWithLease(run.getId());
        }
    }

    /**
     * 带分布式锁执行单个Run推进逻辑
     * 获取锁成功才执行processor；finally中校验锁持有者，仅删除自己持有的锁，防止锁误释放
     */
    private void processWithLease(Long runId) {
        String key = LOCK_PREFIX + runId;
        String owner = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(Math.max(5, properties.getReconciliationLockSeconds()));
        if (!redisUtils.setIfAbsent(key, owner, ttl)) {
            runtimeMetrics.recordReconciliationLockContention();
            return;
        }
        try {
            processor.process(runId);
            runtimeMetrics.recordReconciliationProcessed();
        } finally {
            redisUtils.deleteIfValueMatches(key, owner);
        }
    }
}
