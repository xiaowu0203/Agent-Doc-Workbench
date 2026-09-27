package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEvidenceEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 仅纳入本次报告选中的 Metric 证据和当前 Task 的人工反馈。 */
@Service
@RequiredArgsConstructor
class ExperimentReportEvidenceService {

    private final EvaluationMetricEvidenceMapper metricEvidenceMapper;
    private final EvaluationEvidenceReferenceMapper evidenceMapper;
    private final EvaluationFeedbackMapper feedbackMapper;

    Evidence load(Long spaceId, ExperimentReportSnapshotService.Snapshot snapshot,
                  List<Long> selectedMetricIds) {
        List<EvaluationMetricEvidenceEntity> links = selectedMetricIds.isEmpty() ? List.of()
                : metricEvidenceMapper.selectList(new LambdaQueryWrapper<EvaluationMetricEvidenceEntity>()
                .in(EvaluationMetricEvidenceEntity::getMetricId, selectedMetricIds));
        List<Long> evidenceIds = links.stream().map(EvaluationMetricEvidenceEntity::getEvidenceReferenceId)
                .distinct().sorted().toList();
        Map<Long, EvaluationEvidenceReferenceEntity> references = evidenceIds.isEmpty() ? Map.of()
                : evidenceMapper.selectBatchIds(evidenceIds).stream()
                .collect(Collectors.toMap(EvaluationEvidenceReferenceEntity::getId, Function.identity()));
        List<MetricEvidence> metricEvidence = new ArrayList<>();
        for (EvaluationMetricEvidenceEntity link : links) {
            EvaluationEvidenceReferenceEntity reference = references.get(link.getEvidenceReferenceId());
            if (reference == null || !Objects.equals(reference.getSpaceId(), spaceId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "REPORT_INPUT_INCOMPLETE");
            }
            metricEvidence.add(new MetricEvidence(link.getMetricId(), reference.getId(),
                    reference.getEvidenceType(), reference.getBusinessId(), reference.getContentHash()));
        }
        metricEvidence.sort((left, right) -> {
            int byMetric = left.metricId().compareTo(right.metricId());
            return byMetric == 0 ? left.evidenceId().compareTo(right.evidenceId()) : byMetric;
        });

        List<EvaluationFeedbackEntity> feedback = snapshot.runIds().isEmpty() ? List.of()
                : feedbackMapper.selectList(new LambdaQueryWrapper<EvaluationFeedbackEntity>()
                .eq(EvaluationFeedbackEntity::getSpaceId, spaceId)
                .in(EvaluationFeedbackEntity::getRunId, snapshot.runIds()));
        Map<Long, ExperimentReportSnapshotService.CaseSelection> byTask = snapshot.cases().stream()
                .filter(item -> item.taskId() != null)
                .collect(Collectors.toMap(ExperimentReportSnapshotService.CaseSelection::taskId,
                        Function.identity()));
        List<CaseFeedback> selectedFeedback = feedback.stream().filter(item -> {
            ExperimentReportSnapshotService.CaseSelection selected = byTask.get(item.getTaskId());
            return selected != null && Objects.equals(item.getRunId(), selected.runId())
                    && Objects.equals(item.getCaseRunId(), selected.caseRunId());
        }).map(item -> {
            ExperimentReportSnapshotService.CaseSelection selected = byTask.get(item.getTaskId());
            return new CaseFeedback(item.getId(), selected.variantKey(), selected.testCaseVersionId(),
                    item.getSourceType(), item.getSourceBusinessId(), item.getLabel(), item.getScore());
        }).sorted((left, right) -> left.id().compareTo(right.id())).toList();
        return new Evidence(List.copyOf(metricEvidence), selectedFeedback,
                selectedFeedback.stream().map(CaseFeedback::id).toList(), evidenceIds);
    }

    record Evidence(List<MetricEvidence> metricEvidence, List<CaseFeedback> feedback,
                    List<Long> feedbackIds, List<Long> evidenceIds) { }

    record MetricEvidence(Long metricId, Long evidenceId, String evidenceType,
                          String businessId, String contentHash) { }

    record CaseFeedback(Long id, String variantKey, Long testCaseVersionId,
                        String sourceType, String sourceBusinessId, String label, BigDecimal score) { }
}
