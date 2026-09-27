package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.metric.MetricEffectiveProjection;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentVariantEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 批量读取报告生成时选中的当前 Attempt、Result 和 Metric。 */
@Service
@RequiredArgsConstructor
class ExperimentReportSnapshotService {

    private final EvaluationRunMapper runMapper;
    private final EvaluationCaseRunMapper caseRunMapper;
    private final EvaluationCaseAttemptMapper attemptMapper;
    private final EvaluationResultMapper resultMapper;
    private final EvaluationMetricMapper metricMapper;
    private final EvaluatorVersionMapper evaluatorVersionMapper;

    Snapshot load(ExperimentEntity experiment, ExperimentManifest manifest,
                  List<ExperimentVariantEntity> variants) {
        if (variants.isEmpty() || variants.stream().anyMatch(variant -> variant.getEvaluationRunId() == null)) {
            throw new BusinessException(ErrorCode.CONFLICT, "REPORT_INPUT_INCOMPLETE");
        }
        List<Long> runIds = variants.stream().map(ExperimentVariantEntity::getEvaluationRunId).toList();
        Map<Long, EvaluationRunEntity> runs = runMapper.selectBatchIds(runIds).stream()
                .collect(Collectors.toMap(EvaluationRunEntity::getId, Function.identity()));
        if (runs.size() != variants.size()) {
            throw new BusinessException(ErrorCode.CONFLICT, "REPORT_INPUT_INCOMPLETE");
        }
        for (ExperimentVariantEntity variant : variants) {
            EvaluationRunEntity run = runs.get(variant.getEvaluationRunId());
            if (!Objects.equals(run.getSpaceId(), experiment.getSpaceId())
                    || !Objects.equals(run.getDatasetVersionId(), experiment.getDatasetVersionId())
                    || !Objects.equals(run.getExperimentVariantId(), variant.getId())
                    || !EvaluationRunStatus.valueOf(run.getStatus()).terminal()) {
                throw new BusinessException(ErrorCode.CONFLICT, "REPORT_INPUT_INCOMPLETE");
            }
        }
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseRunEntity>().in(EvaluationCaseRunEntity::getRunId, runIds));
        List<Long> currentAttemptIds = caseRuns.stream().map(EvaluationCaseRunEntity::getCurrentAttemptId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, EvaluationCaseAttemptEntity> attempts = currentAttemptIds.isEmpty() ? Map.of()
                : attemptMapper.selectBatchIds(currentAttemptIds).stream()
                .collect(Collectors.toMap(EvaluationCaseAttemptEntity::getId, Function.identity()));
        for (EvaluationCaseRunEntity caseRun : caseRuns) {
            if (caseRun.getCurrentAttemptId() == null) {
                continue;
            }
            EvaluationCaseAttemptEntity attempt = attempts.get(caseRun.getCurrentAttemptId());
            if (attempt == null || !Objects.equals(attempt.getCaseRunId(), caseRun.getId())
                    || !Objects.equals(attempt.getRunId(), caseRun.getRunId())
                    || !Objects.equals(attempt.getSpaceId(), experiment.getSpaceId())) {
                throw new BusinessException(ErrorCode.CONFLICT, "REPORT_INPUT_INCOMPLETE");
            }
        }
        List<EvaluationResultEntity> allResults = resultMapper.selectList(
                new LambdaQueryWrapper<EvaluationResultEntity>().in(EvaluationResultEntity::getRunId, runIds));
        List<EvaluationMetricEntity> allMetrics = metricMapper.selectList(
                new LambdaQueryWrapper<EvaluationMetricEntity>().in(EvaluationMetricEntity::getRunId, runIds));
        List<EvaluationMetricEntity> effective = MetricEffectiveProjection.select(allMetrics, caseRuns, allResults);
        Map<Long, Set<Long>> expectedEvaluators = manifest.cases().stream().collect(Collectors.toMap(
                ManifestCase::testCaseVersionId,
                item -> item.evaluators().stream().map(ManifestEvaluator::evaluatorVersionId)
                        .collect(Collectors.toSet())));
        Map<Long, Long> caseByAttempt = caseRuns.stream().filter(item -> item.getCurrentAttemptId() != null)
                .collect(Collectors.toMap(EvaluationCaseRunEntity::getCurrentAttemptId,
                        EvaluationCaseRunEntity::getTestCaseVersionId));
        Map<ResultKey, EvaluationResultEntity> latestResults = new HashMap<>();
        for (EvaluationResultEntity result : allResults) {
            Long caseId = caseByAttempt.get(result.getCaseAttemptId());
            if (caseId != null && expectedEvaluators.getOrDefault(caseId, Set.of())
                    .contains(result.getEvaluatorVersionId())) {
                latestResults.merge(new ResultKey(result.getCaseAttemptId(), result.getEvaluatorVersionId()),
                        result, (left, right) -> Comparator
                                .comparing(EvaluationResultEntity::getEvaluationAttemptNo)
                                .thenComparing(EvaluationResultEntity::getId).compare(left, right) >= 0
                                ? left : right);
            }
        }
        Map<Long, Map<Long, EvaluationCaseRunEntity>> casesByRun = caseRuns.stream()
                .collect(Collectors.groupingBy(EvaluationCaseRunEntity::getRunId,
                        Collectors.toMap(EvaluationCaseRunEntity::getTestCaseVersionId, Function.identity())));
        List<Long> evaluatorIds = manifest.cases().stream().flatMap(item -> item.evaluators().stream())
                .map(ManifestEvaluator::evaluatorVersionId).distinct().toList();
        Map<Long, EvaluatorVersionEntity> evaluatorVersions = evaluatorIds.isEmpty() ? Map.of()
                : evaluatorVersionMapper.selectBatchIds(evaluatorIds).stream()
                .collect(Collectors.toMap(EvaluatorVersionEntity::getId, Function.identity()));
        Set<Long> expectedCaseIds = manifest.cases().stream().map(ManifestCase::testCaseVersionId)
                .collect(Collectors.toSet());
        List<ExperimentReportMatrix.VariantMetrics> observations = new ArrayList<>();
        List<CaseSelection> selections = new ArrayList<>();
        for (ExperimentVariantEntity variant : variants) {
            Long runId = variant.getEvaluationRunId();
            Map<Long, EvaluationCaseRunEntity> actualCases = casesByRun.getOrDefault(runId, Map.of());
            if (!expectedCaseIds.containsAll(actualCases.keySet())) {
                throw new BusinessException(ErrorCode.CONFLICT, "REPORT_INPUT_INCOMPLETE");
            }
            Map<Long, String> missing = new HashMap<>();
            Map<ExperimentReportMatrix.EvaluatorKey, String> evaluatorIssues = new HashMap<>();
            for (ManifestCase expected : manifest.cases()) {
                EvaluationCaseRunEntity caseRun = actualCases.get(expected.testCaseVersionId());
                EvaluationCaseAttemptEntity attempt = caseRun == null ? null
                        : attempts.get(caseRun.getCurrentAttemptId());
                missing.put(expected.testCaseVersionId(), missingReason(attempt));
                for (ManifestEvaluator evaluator : expected.evaluators()) {
                    ExperimentReportMatrix.EvaluatorKey key = new ExperimentReportMatrix.EvaluatorKey(
                            expected.testCaseVersionId(), evaluator.evaluatorVersionId());
                    EvaluatorVersionEntity version = evaluatorVersions.get(evaluator.evaluatorVersionId());
                    if (version == null || !Objects.equals(version.getSpaceId(), experiment.getSpaceId())
                            || !Objects.equals(version.getEvaluatorKey(), evaluator.evaluatorKey())
                            || !Objects.equals(version.getContentHash(), evaluator.contentHash())) {
                        evaluatorIssues.put(key, "EVALUATOR_VERSION_MISMATCH");
                        continue;
                    }
                    EvaluationResultEntity result = attempt == null ? null
                            : latestResults.get(new ResultKey(attempt.getId(), evaluator.evaluatorVersionId()));
                    if (result != null && "ERROR".equals(result.getStatus())) {
                        evaluatorIssues.put(key, "EVALUATION_FAILED");
                    } else if (result != null && "SKIPPED".equals(result.getStatus())) {
                        evaluatorIssues.put(key, "EVALUATION_SKIPPED");
                    }
                }
                selections.add(new CaseSelection(variant.getVariantKey(), runId,
                        expected.testCaseVersionId(), caseRun == null ? null : caseRun.getId(),
                        attempt == null ? null : attempt.getId(),
                        attempt == null ? null : attempt.getExecutionTaskId(),
                        attempt == null ? null : attempt.getStatus(),
                        attempt == null ? null : attempt.getFailureCode()));
            }
            observations.add(new ExperimentReportMatrix.VariantMetrics(variant.getVariantKey(), runId,
                    effective.stream().filter(metric -> Objects.equals(metric.getRunId(), runId)).toList(),
                    missing, evaluatorIssues));
        }
        return new Snapshot(List.copyOf(observations), List.copyOf(selections), runIds,
                attempts.keySet().stream().sorted().toList(),
                latestResults.values().stream().map(EvaluationResultEntity::getId).sorted().toList(),
                effective.stream().map(EvaluationMetricEntity::getId).sorted().toList());
    }

    private String missingReason(EvaluationCaseAttemptEntity attempt) {
        if (attempt == null) {
            return "EVIDENCE_MISSING";
        }
        EvaluationAttemptStatus status = EvaluationAttemptStatus.valueOf(attempt.getStatus());
        return switch (status) {
            case CANCELED, CANCEL_PENDING -> "TASK_CANCELED";
            case REPLAY_FAILED -> "EXECUTION_FAILED";
            case EVALUATOR_FAILED -> "EVALUATION_FAILED";
            default -> "EVIDENCE_MISSING";
        };
    }

    record Snapshot(List<ExperimentReportMatrix.VariantMetrics> variants, List<CaseSelection> cases,
                    List<Long> runIds,
                    List<Long> attemptIds, List<Long> resultIds, List<Long> metricIds) { }

    record CaseSelection(String variantKey, Long runId, Long testCaseVersionId, Long caseRunId,
                         Long attemptId, Long taskId, String attemptStatus, String failureCode) { }

    private record ResultKey(Long attemptId, Long evaluatorVersionId) { }
}
