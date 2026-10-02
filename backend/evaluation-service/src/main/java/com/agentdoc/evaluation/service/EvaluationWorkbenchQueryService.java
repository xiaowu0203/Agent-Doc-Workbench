package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.entity.BaseEntity;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluatorMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.ExperimentMapper;
import com.agentdoc.evaluation.mapper.ExperimentVariantMapper;
import com.agentdoc.evaluation.mapper.TestCaseEvaluatorMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentVariantEntity;
import com.agentdoc.evaluation.pojo.entity.TestCaseEvaluatorEntity;
import com.agentdoc.evaluation.pojo.param.EvaluationRunSearchParam;
import com.agentdoc.evaluation.pojo.param.ExperimentSearchParam;
import com.agentdoc.evaluation.pojo.vo.DatasetCaseBindingVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationCaseAttemptHistoryVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationResultSummaryVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationRunSummaryVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentSummaryVO;
import com.agentdoc.evaluation.pojo.vo.TestCaseEvaluatorBindingVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;

/** Phase 5 工作台所需的轻量分页与关联读模型。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EvaluationWorkbenchQueryService {

    private static final Set<String> ERROR_CASE_STATUSES = Set.of(
            EvaluationAttemptStatus.REPLAY_FAILED.name(), EvaluationAttemptStatus.EVALUATOR_FAILED.name(),
            EvaluationAttemptStatus.CANCELED.name());

    private final EvaluationRunMapper runMapper;
    private final EvaluationCaseRunMapper caseRunMapper;
    private final EvaluationCaseAttemptMapper attemptMapper;
    private final EvaluationResultMapper resultMapper;
    private final ExperimentMapper experimentMapper;
    private final ExperimentVariantMapper variantMapper;
    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationDatasetVersionMapper datasetVersionMapper;
    private final EvaluationDatasetCaseMapper datasetCaseMapper;
    private final EvaluationTestCaseMapper testCaseMapper;
    private final EvaluationTestCaseVersionMapper testCaseVersionMapper;
    private final EvaluatorMapper evaluatorMapper;
    private final EvaluatorVersionMapper evaluatorVersionMapper;
    private final TestCaseEvaluatorMapper testCaseEvaluatorMapper;
    private final SpaceAccessService spaceAccessService;

    public PageVO<EvaluationRunSummaryVO> searchRuns(EvaluationRunSearchParam param) {
        param.validate();
        spaceAccessService.requirePermission(param.getSpaceId(), EVALUATION_READ);
        Page<EvaluationRunEntity> page = runMapper.selectPage(page(param),
                new LambdaQueryWrapper<EvaluationRunEntity>()
                        .eq(EvaluationRunEntity::getSpaceId, param.getSpaceId())
                        .eq(param.getStatus() != null, EvaluationRunEntity::getStatus,
                                param.getStatus() == null ? null : param.getStatus().name())
                        .eq(param.getDatasetVersionId() != null, EvaluationRunEntity::getDatasetVersionId,
                                param.getDatasetVersionId())
                        .eq(param.getSingleTestCaseVersionId() != null,
                                EvaluationRunEntity::getSingleTestCaseVersionId,
                                param.getSingleTestCaseVersionId())
                        .eq(param.getExperimentVariantId() != null, EvaluationRunEntity::getExperimentVariantId,
                                param.getExperimentVariantId())
                        .ge(param.getCreatedFrom() != null, EvaluationRunEntity::getCreatedAt,
                                param.getCreatedFrom())
                        .le(param.getCreatedTo() != null, EvaluationRunEntity::getCreatedAt,
                                param.getCreatedTo())
                        .ge(param.getStartedFrom() != null, EvaluationRunEntity::getStartedAt,
                                param.getStartedFrom())
                        .le(param.getStartedTo() != null, EvaluationRunEntity::getStartedAt,
                                param.getStartedTo())
                        .orderByDesc(EvaluationRunEntity::getCreatedAt, EvaluationRunEntity::getId));
        List<EvaluationRunEntity> runs = page.getRecords();
        log.debug("工作台 Run 查询 spaceId={}, status={}, createdRange={}, startedRange={}, pageNum={}, pageSize={}, resultCount={}",
                param.getSpaceId(), param.getStatus(), param.getCreatedFrom() != null || param.getCreatedTo() != null,
                param.getStartedFrom() != null || param.getStartedTo() != null, param.getPageNum(), param.getPageSize(), runs.size());
        if (runs.isEmpty()) {
            return PageVO.of(List.of(), page.getTotal(), param);
        }

        Map<Long, EvaluationDatasetVersionEntity> datasetVersions = byId(selectBatch(datasetVersionMapper,
                nonNullIds(runs.stream().map(EvaluationRunEntity::getDatasetVersionId).toList())));
        Map<Long, EvaluationDatasetEntity> datasets = byId(selectBatch(datasetMapper, nonNullIds(datasetVersions
                .values().stream().map(EvaluationDatasetVersionEntity::getDatasetId).toList())));
        Map<Long, EvaluationTestCaseVersionEntity> testCaseVersions = byId(selectBatch(testCaseVersionMapper,
                nonNullIds(runs.stream().map(EvaluationRunEntity::getSingleTestCaseVersionId).toList())));
        Map<Long, EvaluationTestCaseEntity> testCases = byId(selectBatch(testCaseMapper, nonNullIds(testCaseVersions
                .values().stream().map(EvaluationTestCaseVersionEntity::getTestCaseId).toList())));
        Map<Long, List<EvaluationCaseRunEntity>> casesByRun = caseRunMapper.selectList(
                        new LambdaQueryWrapper<EvaluationCaseRunEntity>()
                                .select(EvaluationCaseRunEntity::getId, EvaluationCaseRunEntity::getRunId,
                                        EvaluationCaseRunEntity::getStatus)
                                .eq(EvaluationCaseRunEntity::getSpaceId, param.getSpaceId())
                                .in(EvaluationCaseRunEntity::getRunId,
                                        runs.stream().map(EvaluationRunEntity::getId).toList()))
                .stream().collect(Collectors.groupingBy(EvaluationCaseRunEntity::getRunId));

        List<EvaluationRunSummaryVO> records = runs.stream().map(run -> {
            EvaluationDatasetVersionEntity datasetVersion = datasetVersions.get(run.getDatasetVersionId());
            EvaluationDatasetEntity dataset = datasetVersion == null ? null
                    : datasets.get(datasetVersion.getDatasetId());
            EvaluationTestCaseVersionEntity testCaseVersion = testCaseVersions.get(
                    run.getSingleTestCaseVersionId());
            EvaluationTestCaseEntity testCase = testCaseVersion == null ? null
                    : testCases.get(testCaseVersion.getTestCaseId());
            requireScope(datasetVersion == null ? null : datasetVersion.getSpaceId(), run.getSpaceId(), datasetVersion != null);
            requireScope(dataset == null ? null : dataset.getSpaceId(), run.getSpaceId(), dataset != null);
            requireScope(testCaseVersion == null ? null : testCaseVersion.getSpaceId(), run.getSpaceId(), testCaseVersion != null);
            requireScope(testCase == null ? null : testCase.getSpaceId(), run.getSpaceId(), testCase != null);
            List<EvaluationCaseRunEntity> cases = casesByRun.getOrDefault(run.getId(), List.of());
            int completed = (int) cases.stream().filter(value ->
                    EvaluationAttemptStatus.COMPLETED.name().equals(value.getStatus())).count();
            int errors = (int) cases.stream().filter(value -> ERROR_CASE_STATUSES.contains(value.getStatus())).count();
            return new EvaluationRunSummaryVO(run.getId(), run.getSpaceId(), run.getDatasetVersionId(),
                    dataset == null ? null : dataset.getName(),
                    datasetVersion == null ? null : datasetVersion.getVersionNo(),
                    run.getSingleTestCaseVersionId(), testCase == null ? null : testCase.getName(),
                    testCaseVersion == null ? null : testCaseVersion.getVersionNo(),
                    run.getExperimentVariantId(), run.getStatus(), run.getPauseReason(), run.getCancelRequested(),
                    run.getCaseCount(), completed, errors, run.getCreatedBy(), run.getCreatedAt(),
                    run.getStartedAt(), run.getFinishedAt(), run.getUpdatedAt());
        }).toList();
        return PageVO.of(records, page.getTotal(), param);
    }

    public PageVO<ExperimentSummaryVO> searchExperiments(ExperimentSearchParam param) {
        param.validate();
        spaceAccessService.requirePermission(param.getSpaceId(), EVALUATION_READ);
        Page<ExperimentEntity> page = experimentMapper.selectPage(page(param),
                new LambdaQueryWrapper<ExperimentEntity>()
                        .eq(ExperimentEntity::getSpaceId, param.getSpaceId())
                        .eq(param.getStatus() != null, ExperimentEntity::getStatus,
                                param.getStatus() == null ? null : param.getStatus().name())
                        .eq(param.getDatasetVersionId() != null, ExperimentEntity::getDatasetVersionId,
                                param.getDatasetVersionId())
                        .ge(param.getCreatedFrom() != null, ExperimentEntity::getCreatedAt, param.getCreatedFrom())
                        .le(param.getCreatedTo() != null, ExperimentEntity::getCreatedAt, param.getCreatedTo())
                        .ge(param.getStartedFrom() != null, ExperimentEntity::getStartedAt, param.getStartedFrom())
                        .le(param.getStartedTo() != null, ExperimentEntity::getStartedAt, param.getStartedTo())
                        .orderByDesc(ExperimentEntity::getCreatedAt, ExperimentEntity::getId));
        List<ExperimentEntity> experiments = page.getRecords();
        log.debug("工作台 Experiment 查询 spaceId={}, status={}, createdRange={}, startedRange={}, pageNum={}, pageSize={}, resultCount={}",
                param.getSpaceId(), param.getStatus(), param.getCreatedFrom() != null || param.getCreatedTo() != null,
                param.getStartedFrom() != null || param.getStartedTo() != null, param.getPageNum(), param.getPageSize(), experiments.size());
        if (experiments.isEmpty()) {
            return PageVO.of(List.of(), page.getTotal(), param);
        }
        Map<Long, EvaluationDatasetVersionEntity> datasetVersions = byId(selectBatch(datasetVersionMapper,
                nonNullIds(experiments.stream().map(ExperimentEntity::getDatasetVersionId).toList())));
        Map<Long, EvaluationDatasetEntity> datasets = byId(selectBatch(datasetMapper, nonNullIds(datasetVersions
                .values().stream().map(EvaluationDatasetVersionEntity::getDatasetId).toList())));
        Map<Long, List<ExperimentVariantEntity>> variantsByExperiment = variantMapper.selectList(
                        new LambdaQueryWrapper<ExperimentVariantEntity>()
                                .in(ExperimentVariantEntity::getExperimentId,
                                        experiments.stream().map(ExperimentEntity::getId).toList()))
                .stream().collect(Collectors.groupingBy(ExperimentVariantEntity::getExperimentId));

        List<ExperimentSummaryVO> records = experiments.stream().map(experiment -> {
            EvaluationDatasetVersionEntity version = datasetVersions.get(experiment.getDatasetVersionId());
            EvaluationDatasetEntity dataset = version == null ? null : datasets.get(version.getDatasetId());
            requireScope(version == null ? null : version.getSpaceId(), experiment.getSpaceId(), version != null);
            requireScope(dataset == null ? null : dataset.getSpaceId(), experiment.getSpaceId(), dataset != null);
            List<ExperimentVariantEntity> variants = variantsByExperiment.getOrDefault(experiment.getId(), List.of());
            int linkedRuns = (int) variants.stream().filter(value -> value.getEvaluationRunId() != null).count();
            return new ExperimentSummaryVO(experiment.getId(), experiment.getSpaceId(),
                    experiment.getDatasetVersionId(), dataset == null ? null : dataset.getName(),
                    version == null ? null : version.getVersionNo(), experiment.getStatus(), variants.size(),
                    linkedRuns, experiment.getAuthorizedTokenBudget(), experiment.getFailureCode(),
                    experiment.getDecision(), experiment.getDecisionReportRevision(), experiment.getCreatedBy(),
                    experiment.getCreatedAt(), experiment.getStartedAt(), experiment.getFinishedAt(),
                    experiment.getUpdatedAt());
        }).toList();
        return PageVO.of(records, page.getTotal(), param);
    }

    public List<DatasetCaseBindingVO> datasetCases(Long datasetVersionId) {
        EvaluationDatasetVersionEntity version = datasetVersionMapper.selectById(datasetVersionId);
        requireReadable(version == null ? null : version.getSpaceId(), "DatasetVersion");
        List<EvaluationDatasetCaseEntity> bindings = datasetCaseMapper.selectList(
                new LambdaQueryWrapper<EvaluationDatasetCaseEntity>()
                        .eq(EvaluationDatasetCaseEntity::getDatasetVersionId, datasetVersionId)
                        .orderByAsc(EvaluationDatasetCaseEntity::getSortOrder, EvaluationDatasetCaseEntity::getId));
        Map<Long, EvaluationTestCaseVersionEntity> versions = byId(selectBatch(testCaseVersionMapper,
                nonNullIds(bindings.stream().map(EvaluationDatasetCaseEntity::getTestCaseVersionId).toList())));
        Map<Long, EvaluationTestCaseEntity> testCases = byId(selectBatch(testCaseMapper, nonNullIds(versions
                .values().stream().map(EvaluationTestCaseVersionEntity::getTestCaseId).toList())));
        return bindings.stream().map(binding -> {
            EvaluationTestCaseVersionEntity target = versions.get(binding.getTestCaseVersionId());
            EvaluationTestCaseEntity testCase = target == null ? null : testCases.get(target.getTestCaseId());
            requireScope(target == null ? null : target.getSpaceId(), version.getSpaceId(), target != null);
            requireScope(testCase == null ? null : testCase.getSpaceId(), version.getSpaceId(), testCase != null);
            return new DatasetCaseBindingVO(binding.getId(), binding.getTestCaseVersionId(),
                    target == null ? null : target.getTestCaseId(), testCase == null ? null : testCase.getName(),
                    target == null ? null : target.getVersionNo(), target == null ? null : target.getStatus(),
                    binding.getSortOrder(), binding.getEnabled());
        }).toList();
    }

    public List<TestCaseEvaluatorBindingVO> testCaseEvaluators(Long testCaseVersionId) {
        EvaluationTestCaseVersionEntity version = testCaseVersionMapper.selectById(testCaseVersionId);
        requireReadable(version == null ? null : version.getSpaceId(), "TestCaseVersion");
        List<TestCaseEvaluatorEntity> bindings = testCaseEvaluatorMapper.selectList(
                new LambdaQueryWrapper<TestCaseEvaluatorEntity>()
                        .eq(TestCaseEvaluatorEntity::getTestCaseVersionId, testCaseVersionId)
                        .orderByAsc(TestCaseEvaluatorEntity::getSortOrder, TestCaseEvaluatorEntity::getId));
        Map<Long, EvaluatorVersionEntity> versions = byId(selectBatch(evaluatorVersionMapper, nonNullIds(bindings
                .stream().map(TestCaseEvaluatorEntity::getEvaluatorVersionId).toList())));
        Map<Long, EvaluatorEntity> evaluators = byId(selectBatch(evaluatorMapper, nonNullIds(versions.values()
                .stream().map(EvaluatorVersionEntity::getEvaluatorId).toList())));
        return bindings.stream().map(binding -> {
            EvaluatorVersionEntity target = versions.get(binding.getEvaluatorVersionId());
            EvaluatorEntity evaluator = target == null ? null : evaluators.get(target.getEvaluatorId());
            requireScope(target == null ? null : target.getSpaceId(), version.getSpaceId(), target != null);
            requireScope(evaluator == null ? null : evaluator.getSpaceId(), version.getSpaceId(), evaluator != null);
            return new TestCaseEvaluatorBindingVO(binding.getId(), binding.getEvaluatorVersionId(),
                    target == null ? null : target.getEvaluatorId(), evaluator == null ? null : evaluator.getName(),
                    target == null ? null : target.getVersionNo(), target == null ? null : target.getStatus(),
                    target == null ? null : target.getEvaluatorKey(), binding.getSortOrder(), binding.getExpectedJson());
        }).toList();
    }

    public PageVO<EvaluationCaseAttemptHistoryVO> caseAttempts(Long caseRunId, PageParam param) {
        param.validate();
        EvaluationCaseRunEntity caseRun = caseRunMapper.selectById(caseRunId);
        EvaluationRunEntity run = caseRun == null ? null : runMapper.selectById(caseRun.getRunId());
        if (caseRun == null || run == null || !run.getId().equals(caseRun.getRunId())
                || !run.getSpaceId().equals(caseRun.getSpaceId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CaseRun 不存在");
        }
        spaceAccessService.requirePermission(run.getSpaceId(), EVALUATION_READ);
        Page<EvaluationCaseAttemptEntity> page = attemptMapper.selectPage(page(param),
                new LambdaQueryWrapper<EvaluationCaseAttemptEntity>()
                        .eq(EvaluationCaseAttemptEntity::getCaseRunId, caseRunId)
                        .eq(EvaluationCaseAttemptEntity::getRunId, run.getId())
                        .eq(EvaluationCaseAttemptEntity::getSpaceId, run.getSpaceId())
                        .orderByDesc(EvaluationCaseAttemptEntity::getAttemptNo,
                                EvaluationCaseAttemptEntity::getId));
        List<EvaluationCaseAttemptEntity> attempts = page.getRecords();
        if (attempts.isEmpty()) {
            return PageVO.of(List.of(), page.getTotal(), param);
        }
        List<EvaluationResultEntity> results = resultMapper.selectList(
                new LambdaQueryWrapper<EvaluationResultEntity>()
                        .eq(EvaluationResultEntity::getRunId, run.getId())
                        .eq(EvaluationResultEntity::getSpaceId, run.getSpaceId())
                        .in(EvaluationResultEntity::getCaseAttemptId,
                                attempts.stream().map(EvaluationCaseAttemptEntity::getId).toList())
                        .orderByAsc(EvaluationResultEntity::getEvaluatorVersionId)
                        .orderByDesc(EvaluationResultEntity::getEvaluationAttemptNo,
                                EvaluationResultEntity::getId));
        Map<Long, EvaluatorVersionEntity> evaluatorVersions = byId(selectBatch(evaluatorVersionMapper, nonNullIds(
                results.stream().map(EvaluationResultEntity::getEvaluatorVersionId).toList())));
        Map<Long, EvaluatorEntity> evaluators = byId(selectBatch(evaluatorMapper, nonNullIds(evaluatorVersions
                .values().stream().map(EvaluatorVersionEntity::getEvaluatorId).toList())));
        Map<Long, List<EvaluationResultEntity>> resultsByAttempt = results.stream()
                .collect(Collectors.groupingBy(EvaluationResultEntity::getCaseAttemptId));

        List<EvaluationCaseAttemptHistoryVO> records = attempts.stream().map(attempt -> {
            List<EvaluationResultEntity> attemptResults = resultsByAttempt.getOrDefault(attempt.getId(), List.of());
            Map<Long, Integer> currentNumbers = attemptResults.stream().collect(Collectors.toMap(
                    EvaluationResultEntity::getEvaluatorVersionId,
                    EvaluationResultEntity::getEvaluationAttemptNo, Math::max));
            List<EvaluationResultSummaryVO> summaries = attemptResults.stream().map(result -> {
                EvaluatorVersionEntity evaluatorVersion = evaluatorVersions.get(result.getEvaluatorVersionId());
                EvaluatorEntity evaluator = evaluatorVersion == null ? null
                        : evaluators.get(evaluatorVersion.getEvaluatorId());
                requireScope(evaluatorVersion == null ? null : evaluatorVersion.getSpaceId(), run.getSpaceId(), evaluatorVersion != null);
                requireScope(evaluator == null ? null : evaluator.getSpaceId(), run.getSpaceId(), evaluator != null);
                boolean current = result.getEvaluationAttemptNo().equals(
                        currentNumbers.get(result.getEvaluatorVersionId()));
                return new EvaluationResultSummaryVO(result.getId(), result.getEvaluatorVersionId(),
                        evaluator == null ? null : evaluator.getName(),
                        evaluatorVersion == null ? null : evaluatorVersion.getVersionNo(),
                        result.getEvaluationAttemptNo(), current, result.getStatus(), result.getScore(),
                        result.getSummaryCode(), result.getTraceId(), result.getSpanId(), result.getStartedAt(),
                        result.getFinishedAt());
            }).toList();
            return new EvaluationCaseAttemptHistoryVO(attempt.getId(), attempt.getAttemptNo(),
                    attempt.getId().equals(caseRun.getCurrentAttemptId()), attempt.getReplayTaskId(),
                    attempt.getExecutionTaskId(), attempt.getStatus(), attempt.getFailureStage(),
                    attempt.getFailureCode(), attempt.getFailureMessage(), attempt.getStartedAt(),
                    attempt.getFinishedAt(), attempt.getUpdatedAt(), summaries);
        }).toList();
        return PageVO.of(records, page.getTotal(), param);
    }

    private void requireReadable(Long spaceId, String resource) {
        if (spaceId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, resource + " 不存在");
        }
        spaceAccessService.requirePermission(spaceId, EVALUATION_READ);
    }

    private static void requireScope(Long actual, Long expected, boolean present) {
        if (present && !Objects.equals(actual, expected)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作台关联资源不存在");
        }
    }

    private static <T extends BaseEntity> Map<Long, T> byId(List<T> values) {
        return values.stream().collect(Collectors.toMap(T::getId, Function.identity(), (left, right) -> left));
    }

    private static List<Long> nonNullIds(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static <T> List<T> selectBatch(BaseMapper<T> mapper, List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectBatchIds(ids);
    }

    private static <T> Page<T> page(PageParam param) {
        return new Page<>(param.getPageNum(), param.getPageSize());
    }
}
