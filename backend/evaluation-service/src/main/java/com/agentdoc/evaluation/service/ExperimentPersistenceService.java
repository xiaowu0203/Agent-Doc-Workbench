package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.enums.ExperimentStatus;
import com.agentdoc.evaluation.mapper.ExperimentMapper;
import com.agentdoc.evaluation.mapper.ExperimentVariantMapper;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentVariantEntity;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Experiment 本地短事务写入边界。 */
@Service
@RequiredArgsConstructor
public class ExperimentPersistenceService {

    private final ExperimentMapper experimentMapper;
    private final ExperimentVariantMapper variantMapper;

    @Transactional
    public void create(ExperimentEntity experiment, List<ExperimentVariantEntity> variants) {
        experimentMapper.insert(experiment);
        for (ExperimentVariantEntity variant : variants) {
            variantMapper.insert(variant);
        }
    }

    @Transactional
    public boolean acceptStart(Long experimentId, Long userId, Long authorizedTokenBudget) {
        LocalDateTime now = LocalDateTime.now();
        int updated = experimentMapper.update(null, new LambdaUpdateWrapper<ExperimentEntity>()
                .eq(ExperimentEntity::getId, experimentId)
                .in(ExperimentEntity::getStatus, ExperimentStatus.CREATED.name(), ExperimentStatus.PAUSED.name())
                .set(ExperimentEntity::getStatus, ExperimentStatus.STARTING.name())
                .set(ExperimentEntity::getAuthorizedTokenBudget, authorizedTokenBudget)
                .set(ExperimentEntity::getStartedBy, userId)
                .set(ExperimentEntity::getStartedAt, now)
                .set(ExperimentEntity::getFailureCode, null)
                .set(ExperimentEntity::getFailureMessage, null));
        return updated == 1;
    }

    @Transactional
    public void linkRun(Long variantId, Long runId) {
        variantMapper.update(null, new LambdaUpdateWrapper<ExperimentVariantEntity>()
                .eq(ExperimentVariantEntity::getId, variantId)
                .and(wrapper -> wrapper.isNull(ExperimentVariantEntity::getEvaluationRunId)
                        .or().eq(ExperimentVariantEntity::getEvaluationRunId, runId))
                .set(ExperimentVariantEntity::getEvaluationRunId, runId));
    }

    @Transactional
    public void updateStatus(Long experimentId, ExperimentStatus status, String failureCode,
                             String failureMessage) {
        ExperimentEntity experiment = new ExperimentEntity();
        experiment.setId(experimentId);
        experiment.setStatus(status.name());
        experiment.setFailureCode(failureCode);
        experiment.setFailureMessage(failureMessage);
        if (status.terminal()) {
            experiment.setFinishedAt(LocalDateTime.now());
        }
        experimentMapper.updateById(experiment);
    }

    @Transactional
    public void requestCancel(Long experimentId, Long userId) {
        ExperimentEntity experiment = new ExperimentEntity();
        experiment.setId(experimentId);
        experiment.setStatus(ExperimentStatus.CANCEL_PENDING.name());
        experiment.setCancelRequestedBy(userId);
        experiment.setCancelRequestedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);
    }
}
