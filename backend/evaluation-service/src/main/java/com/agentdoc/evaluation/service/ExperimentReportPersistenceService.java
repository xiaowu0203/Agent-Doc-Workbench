package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.enums.ExperimentStatus;
import com.agentdoc.evaluation.mapper.ExperimentMapper;
import com.agentdoc.evaluation.mapper.ExperimentReportMapper;
import com.agentdoc.evaluation.mapper.ExperimentReportRequestMapper;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentReportEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentReportRequestEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** 锁定 Experiment 后分配报告 revision，历史报告行只插入不改写。 */
@Service
@RequiredArgsConstructor
class ExperimentReportPersistenceService {

    private final ExperimentMapper experimentMapper;
    private final ExperimentReportMapper reportMapper;
    private final ExperimentReportRequestMapper requestMapper;

    public ExperimentReportEntity existingRequest(Long experimentId, String requestKey, String requestHash) {
        ExperimentReportRequestEntity prior = requestMapper.selectOne(
                new LambdaQueryWrapper<ExperimentReportRequestEntity>()
                        .eq(ExperimentReportRequestEntity::getExperimentId, experimentId)
                        .eq(ExperimentReportRequestEntity::getRequestKey, requestKey));
        if (prior == null) {
            return null;
        }
        if (!Objects.equals(prior.getRequestHash(), requestHash)) {
            throw new BusinessException(ErrorCode.CONFLICT, "报告重算幂等键已被不同请求使用");
        }
        return reportMapper.selectById(prior.getReportId());
    }

    @Transactional
    public ExperimentReportEntity save(Long experimentId, Prepared prepared, String requestKey,
                                       String requestHash, Long generatedBy) {
        ExperimentEntity experiment = lock(experimentId);
        ExperimentStatus status = ExperimentStatus.valueOf(experiment.getStatus());
        if (status != ExperimentStatus.COMPLETED && status != ExperimentStatus.COMPLETED_WITH_ERRORS) {
            throw new BusinessException(ErrorCode.CONFLICT, "REPORT_INPUT_INCOMPLETE");
        }
        if (!Objects.equals(experiment.getManifestHash(), prepared.manifestHash())) {
            throw new BusinessException(ErrorCode.CONFLICT, "MANIFEST_MISMATCH");
        }
        if (requestKey != null) {
            ExperimentReportEntity prior = existingRequest(experimentId, requestKey, requestHash);
            if (prior != null) {
                return prior;
            }
        }
        ExperimentReportEntity latest = reportMapper.selectOne(new LambdaQueryWrapper<ExperimentReportEntity>()
                .eq(ExperimentReportEntity::getExperimentId, experimentId)
                .orderByDesc(ExperimentReportEntity::getRevision).last("LIMIT 1"));
        if (requestKey == null && latest != null) {
            return latest;
        }
        ExperimentReportEntity report = reportMapper.selectOne(new LambdaQueryWrapper<ExperimentReportEntity>()
                .eq(ExperimentReportEntity::getExperimentId, experimentId)
                .eq(ExperimentReportEntity::getCalculationInputHash, prepared.calculationInputHash()));
        if (report == null) {
            report = new ExperimentReportEntity();
            report.setId(IdWorker.getId());
            report.setExperimentId(experimentId);
            report.setSpaceId(experiment.getSpaceId());
            report.setRevision(latest == null ? 1 : latest.getRevision() + 1);
            report.setManifestHash(prepared.manifestHash());
            report.setCalculationSchemaVersion(prepared.calculationSchemaVersion());
            report.setCalculationInputHash(prepared.calculationInputHash());
            report.setSelectedRecordIdsJson(prepared.selectedRecordIdsJson());
            report.setReportSchemaVersion(prepared.reportSchemaVersion());
            report.setReportJson(prepared.reportJson());
            report.setContentHash(prepared.contentHash());
            report.setRecalculationRequestKey(requestKey);
            report.setRecalculationRequestHash(requestHash);
            report.setGeneratedBy(generatedBy);
            reportMapper.insert(report);
        }
        if (requestKey != null) {
            ExperimentReportRequestEntity request = new ExperimentReportRequestEntity();
            request.setId(IdWorker.getId());
            request.setExperimentId(experimentId);
            request.setSpaceId(experiment.getSpaceId());
            request.setRequestKey(requestKey);
            request.setRequestHash(requestHash);
            request.setReportId(report.getId());
            request.setCreatedBy(generatedBy);
            requestMapper.insert(request);
        }
        return report;
    }

    @Transactional
    public ExperimentEntity decide(Long experimentId, Integer revision, String decision,
                                   String reason, Long decidedBy) {
        ExperimentEntity experiment = lock(experimentId);
        ExperimentStatus status = ExperimentStatus.valueOf(experiment.getStatus());
        if (status != ExperimentStatus.COMPLETED && status != ExperimentStatus.COMPLETED_WITH_ERRORS) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有已完成 Experiment 可作人工结论");
        }
        ExperimentReportEntity report = reportMapper.selectOne(new LambdaQueryWrapper<ExperimentReportEntity>()
                .eq(ExperimentReportEntity::getExperimentId, experimentId)
                .eq(ExperimentReportEntity::getRevision, revision));
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告 revision 不存在");
        }
        if (experiment.getDecision() != null) {
            if (Objects.equals(experiment.getDecision(), decision)
                    && Objects.equals(experiment.getDecisionReason(), reason)
                    && Objects.equals(experiment.getDecisionReportRevision(), revision)
                    && Objects.equals(experiment.getDecidedBy(), decidedBy)) {
                return experiment;
            }
            throw new BusinessException(ErrorCode.CONFLICT, "Experiment 已有人工结论");
        }
        int updated = experimentMapper.update(null, new LambdaUpdateWrapper<ExperimentEntity>()
                .eq(ExperimentEntity::getId, experimentId)
                .isNull(ExperimentEntity::getDecision)
                .set(ExperimentEntity::getDecision, decision)
                .set(ExperimentEntity::getDecisionReason, reason)
                .set(ExperimentEntity::getDecisionReportRevision, revision)
                .set(ExperimentEntity::getDecidedBy, decidedBy)
                .set(ExperimentEntity::getDecidedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Experiment 人工结论写入冲突");
        }
        return experimentMapper.selectById(experimentId);
    }

    private ExperimentEntity lock(Long experimentId) {
        ExperimentEntity experiment = experimentMapper.selectOne(new LambdaQueryWrapper<ExperimentEntity>()
                .eq(ExperimentEntity::getId, experimentId).last("FOR UPDATE"));
        if (experiment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Experiment 不存在");
        }
        return experiment;
    }

    record Prepared(String manifestHash, int calculationSchemaVersion, String calculationInputHash,
                    String selectedRecordIdsJson, int reportSchemaVersion, String reportJson,
                    String contentHash) { }
}
