package com.agentdoc.evaluation.service;

import com.agentdoc.common.utils.RedisUtils;
import com.agentdoc.evaluation.config.EvaluationRuntimeProperties;
import com.agentdoc.evaluation.enums.ExperimentStatus;
import com.agentdoc.evaluation.mapper.ExperimentMapper;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 多实例安全的 Experiment 顶层状态对账。 */
@Component
@RequiredArgsConstructor
public class ExperimentReconciliationJob {

    private static final String LOCK_PREFIX = "evaluation:reconcile:experiment:";

    private final ExperimentMapper experimentMapper;
    private final ExperimentService experimentService;
    private final ExperimentPersistenceService persistenceService;
    private final EvaluationRuntimeProperties properties;
    private final RedisUtils redisUtils;

    @Scheduled(fixedDelayString = "${agent-doc.evaluation.runtime.reconciliation-interval-ms:5000}")
    public void reconcile() {
        if (!properties.isReconciliationEnabled()) {
            return;
        }
        int batchSize = Math.max(1, Math.min(properties.getReconciliationBatchSize(), 100));
        List<ExperimentEntity> experiments = experimentMapper.selectList(
                new LambdaQueryWrapper<ExperimentEntity>()
                        .in(ExperimentEntity::getStatus, ExperimentStatus.STARTING.name(),
                                ExperimentStatus.RUNNING.name(), ExperimentStatus.CANCEL_PENDING.name())
                        .orderByAsc(ExperimentEntity::getUpdatedAt, ExperimentEntity::getId)
                        .last("LIMIT " + batchSize));
        for (ExperimentEntity experiment : experiments) {
            processWithLease(experiment.getId());
        }
    }

    private void processWithLease(Long experimentId) {
        String key = LOCK_PREFIX + experimentId;
        String owner = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(Math.max(5, properties.getReconciliationLockSeconds()));
        if (!redisUtils.setIfAbsent(key, owner, ttl)) {
            return;
        }
        try {
            ExperimentEntity experiment = experimentMapper.selectById(experimentId);
            LocalDateTime updatedAt = experiment == null ? null : experiment.getUpdatedAt();
            if (experiment != null && ExperimentStatus.STARTING.name().equals(experiment.getStatus())
                    && updatedAt != null && updatedAt.isBefore(LocalDateTime.now()
                    .minusSeconds(Math.max(5, properties.getDispatchStaleSeconds())))) {
                persistenceService.updateStatus(experimentId, ExperimentStatus.PAUSED,
                        "RECONCILIATION_BLOCKED", "Experiment 启动中断，可显式恢复");
                return;
            }
            experimentService.reconcile(experimentId);
        } finally {
            redisUtils.deleteIfValueMatches(key, owner);
        }
    }
}
