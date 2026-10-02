package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
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
import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentVariantEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorEntity;
import com.agentdoc.evaluation.pojo.entity.TestCaseEvaluatorEntity;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import com.agentdoc.evaluation.enums.ExperimentStatus;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import com.agentdoc.evaluation.pojo.param.EvaluationRunSearchParam;
import com.agentdoc.evaluation.pojo.param.ExperimentSearchParam;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationWorkbenchQueryServiceTest {

    @BeforeAll
    static void initializeMapperMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "workbench-tests");
        for (Class<?> type : List.of(EvaluationCaseRunEntity.class, EvaluationRunEntity.class,
                ExperimentEntity.class, EvaluationCaseAttemptEntity.class, EvaluationResultEntity.class,
                EvaluationDatasetCaseEntity.class, TestCaseEvaluatorEntity.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @Mock private EvaluationRunMapper runMapper;
    @Mock private EvaluationCaseRunMapper caseRunMapper;
    @Mock private EvaluationCaseAttemptMapper attemptMapper;
    @Mock private EvaluationResultMapper resultMapper;
    @Mock private ExperimentMapper experimentMapper;
    @Mock private ExperimentVariantMapper variantMapper;
    @Mock private EvaluationDatasetMapper datasetMapper;
    @Mock private EvaluationDatasetVersionMapper datasetVersionMapper;
    @Mock private EvaluationDatasetCaseMapper datasetCaseMapper;
    @Mock private EvaluationTestCaseMapper testCaseMapper;
    @Mock private EvaluationTestCaseVersionMapper testCaseVersionMapper;
    @Mock private EvaluatorMapper evaluatorMapper;
    @Mock private EvaluatorVersionMapper evaluatorVersionMapper;
    @Mock private TestCaseEvaluatorMapper testCaseEvaluatorMapper;
    @Mock private SpaceAccessService spaceAccessService;
    @InjectMocks private EvaluationWorkbenchQueryService service;

    @Test
    void batchesRunNamesAndCountsWithoutExpandingAttempts() {
        EvaluationRunEntity first = run(1L);
        first.setDatasetVersionId(11L);
        first.setCaseCount(3);
        EvaluationRunEntity second = run(2L);
        second.setDatasetVersionId(11L);
        second.setCaseCount(0);
        when(runMapper.selectPage(any(Page.class), any())).thenReturn(
                new Page<EvaluationRunEntity>(2, 10, 12).setRecords(List.of(first, second)));
        when(datasetVersionMapper.selectBatchIds(List.of(11L))).thenReturn(List.of(datasetVersion()));
        when(datasetMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(dataset()));
        when(caseRunMapper.selectList(any())).thenReturn(List.of(
                caseRun(31L, 1L, "COMPLETED"), caseRun(32L, 1L, "REPLAY_FAILED"),
                caseRun(33L, 1L, "EVALUATING")));
        EvaluationRunSearchParam param = runSearch();
        param.setPageNum(2);

        var result = service.searchRuns(param);

        assertThat(result.total()).isEqualTo(12);
        assertThat(result.pageNum()).isEqualTo(2);
        assertThat(result.records()).hasSize(2);
        assertThat(result.records().getFirst().datasetName()).isEqualTo("回归数据集");
        assertThat(result.records().getFirst().datasetVersionNo()).isEqualTo(3);
        assertThat(result.records().getFirst().completedCaseCount()).isEqualTo(1);
        assertThat(result.records().getFirst().errorCaseCount()).isEqualTo(1);
        assertThat(result.records().getLast().completedCaseCount()).isZero();
        verify(spaceAccessService).requirePermission(9L, EVALUATION_READ);
        verify(datasetVersionMapper).selectBatchIds(List.of(11L));
        verify(datasetMapper).selectBatchIds(List.of(10L));
        verifyNoInteractions(attemptMapper, resultMapper, testCaseMapper, testCaseVersionMapper);
    }

    @Test
    void emptyPagePreservesTotalAndSkipsAssociationQueries() {
        when(runMapper.selectPage(any(Page.class), any())).thenReturn(new Page<EvaluationRunEntity>(3, 10, 15));

        var result = service.searchRuns(runSearch());

        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isEqualTo(15);
        verifyNoInteractions(datasetVersionMapper, datasetMapper, caseRunMapper, attemptMapper, resultMapper);
    }

    @Test
    void deniedSpaceCannotQueryRunsOrTheirAssociations() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权限"))
                .when(spaceAccessService).requirePermission(9L, EVALUATION_READ);

        assertThatThrownBy(() -> service.searchRuns(runSearch())).isInstanceOf(BusinessException.class);

        verifyNoInteractions(runMapper, datasetVersionMapper, datasetMapper, caseRunMapper);
    }

    @Test
    void invalidSpacePageAndTimeRangesFailBeforeDatabaseAccess() {
        EvaluationRunSearchParam param = runSearch();
        param.setSpaceId(null);
        assertBadRequest(() -> service.searchRuns(param));
        param.setSpaceId(9L);
        param.setPageSize(101);
        assertBadRequest(() -> service.searchRuns(param));
        param.setPageSize(10);
        param.setCreatedFrom(LocalDateTime.of(2026, 9, 29, 0, 0));
        param.setCreatedTo(LocalDateTime.of(2026, 9, 28, 0, 0));
        assertBadRequest(() -> service.searchRuns(param));
        ExperimentSearchParam experiment = new ExperimentSearchParam();
        experiment.setSpaceId(9L);
        experiment.setStartedFrom(param.getCreatedFrom());
        experiment.setStartedTo(param.getCreatedTo());
        assertBadRequest(() -> service.searchExperiments(experiment));
        verifyNoInteractions(spaceAccessService, runMapper, experimentMapper);
    }

    @Test
    void experimentSummaryCountsOnlyLinkedVariants() {
        ExperimentEntity experiment = new ExperimentEntity();
        experiment.setId(4L);
        experiment.setSpaceId(9L);
        experiment.setDatasetVersionId(11L);
        experiment.setAuthorizedTokenBudget(12000L);
        when(experimentMapper.selectPage(any(Page.class), any())).thenReturn(
                new Page<ExperimentEntity>(1, 10, 1).setRecords(List.of(experiment)));
        when(datasetVersionMapper.selectBatchIds(List.of(11L))).thenReturn(List.of(datasetVersion()));
        when(datasetMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(dataset()));
        ExperimentVariantEntity baseline = new ExperimentVariantEntity();
        baseline.setExperimentId(4L);
        baseline.setEvaluationRunId(1L);
        ExperimentVariantEntity candidate = new ExperimentVariantEntity();
        candidate.setExperimentId(4L);
        when(variantMapper.selectList(any())).thenReturn(List.of(baseline, candidate));
        ExperimentSearchParam param = new ExperimentSearchParam();
        param.setSpaceId(9L);

        var summary = service.searchExperiments(param).records().getFirst();

        assertThat(summary.variantCount()).isEqualTo(2);
        assertThat(summary.linkedRunCount()).isEqualTo(1);
        assertThat(summary.authorizedTokenBudget()).isEqualTo(12000L);
        assertThat(summary.datasetName()).isEqualTo("回归数据集");
        verify(spaceAccessService).requirePermission(9L, EVALUATION_READ);
    }

    @Test
    void datasetBindingsKeepFrozenOrderAndArchivedVersionReadable() {
        EvaluationDatasetVersionEntity version = datasetVersion();
        version.setStatus("ARCHIVED");
        when(datasetVersionMapper.selectById(11L)).thenReturn(version);
        EvaluationDatasetCaseEntity binding = new EvaluationDatasetCaseEntity();
        binding.setId(51L);
        binding.setTestCaseVersionId(21L);
        binding.setEnabled(false);
        binding.setSortOrder(7);
        when(datasetCaseMapper.selectList(any())).thenReturn(List.of(binding));
        EvaluationTestCaseVersionEntity caseVersion = new EvaluationTestCaseVersionEntity();
        caseVersion.setId(21L);
        caseVersion.setSpaceId(9L);
        caseVersion.setTestCaseId(20L);
        caseVersion.setVersionNo(2);
        caseVersion.setStatus("PUBLISHED");
        when(testCaseVersionMapper.selectBatchIds(List.of(21L))).thenReturn(List.of(caseVersion));
        EvaluationTestCaseEntity testCase = new EvaluationTestCaseEntity();
        testCase.setId(20L);
        testCase.setSpaceId(9L);
        testCase.setName("文档检查");
        when(testCaseMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(testCase));

        var result = service.datasetCases(11L).getFirst();

        assertThat(result.testCaseName()).isEqualTo("文档检查");
        assertThat(result.versionNo()).isEqualTo(2);
        assertThat(result.sortOrder()).isEqualTo(7);
        assertThat(result.enabled()).isFalse();
        verify(spaceAccessService).requirePermission(9L, EVALUATION_READ);
    }

    @Test
    void attemptsExposeCurrentCaseAndLatestResultPerEvaluatorWithoutPrivatePayload() {
        EvaluationCaseRunEntity caseRun = caseRun(31L, 1L, "COMPLETED");
        caseRun.setCurrentAttemptId(42L);
        when(caseRunMapper.selectById(31L)).thenReturn(caseRun);
        when(runMapper.selectById(1L)).thenReturn(run(1L));
        EvaluationCaseAttemptEntity current = attempt(42L, 2);
        EvaluationCaseAttemptEntity previous = attempt(41L, 1);
        when(attemptMapper.selectPage(any(Page.class), any())).thenReturn(
                new Page<EvaluationCaseAttemptEntity>(1, 20, 2).setRecords(List.of(current, previous)));
        EvaluationResultEntity newest = result(63L, 42L, 71L, 2);
        newest.setDetailsJson("private diagnostics");
        when(resultMapper.selectList(any())).thenReturn(List.of(newest,
                result(62L, 42L, 71L, 1), result(64L, 42L, 72L, 1), result(61L, 41L, 71L, 1)));
        when(evaluatorVersionMapper.selectBatchIds(any())).thenReturn(List.of());
        PageParam param = new PageParam();
        param.setPageSize(20);

        var page = service.caseAttempts(31L, param);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).extracting(value -> value.currentCaseAttempt()).containsExactly(true, false);
        assertThat(page.records().getFirst().results()).extracting(value -> value.currentEvaluationResultAttempt())
                .containsExactly(true, false, true);
        assertThat(page.records().getLast().results().getFirst().currentEvaluationResultAttempt()).isTrue();
        verify(resultMapper).selectList(any());
        verify(evaluatorVersionMapper).selectBatchIds(List.of(71L, 72L));
        verify(spaceAccessService).requirePermission(9L, EVALUATION_READ);
        verifyNoInteractions(evaluatorMapper);
    }

    @Test
    void rejectsInconsistentCaseRunSpaceBeforeLoadingHistory() {
        EvaluationCaseRunEntity caseRun = caseRun(31L, 1L, "COMPLETED");
        caseRun.setSpaceId(8L);
        when(caseRunMapper.selectById(31L)).thenReturn(caseRun);
        when(runMapper.selectById(1L)).thenReturn(run(1L));

        assertThatThrownBy(() -> service.caseAttempts(31L, new PageParam()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));

        verifyNoInteractions(spaceAccessService, attemptMapper, resultMapper);
    }

    @Test
    void deniedCaseRunSpaceCannotLoadHistory() {
        when(caseRunMapper.selectById(31L)).thenReturn(caseRun(31L, 1L, "COMPLETED"));
        when(runMapper.selectById(1L)).thenReturn(run(1L));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权限"))
                .when(spaceAccessService).requirePermission(9L, EVALUATION_READ);

        assertThatThrownBy(() -> service.caseAttempts(31L, new PageParam())).isInstanceOf(BusinessException.class);

        verifyNoInteractions(attemptMapper, resultMapper, evaluatorVersionMapper);
    }

    @Test
    void missingVersionCannotReadBindings() {
        assertThatThrownBy(() -> service.datasetCases(11L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.testCaseEvaluators(21L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(spaceAccessService, datasetCaseMapper, testCaseEvaluatorMapper);
    }

    @Test
    void evaluatorBindingsRetainExpectedJsonAndLoadNamesInBatches() {
        EvaluationTestCaseVersionEntity source = new EvaluationTestCaseVersionEntity();
        source.setId(21L); source.setSpaceId(9L);
        when(testCaseVersionMapper.selectById(21L)).thenReturn(source);
        TestCaseEvaluatorEntity binding = new TestCaseEvaluatorEntity();
        binding.setId(50L); binding.setEvaluatorVersionId(71L); binding.setSortOrder(3);
        binding.setExpectedJson("{\"accepted\":false,\"score\":0}");
        when(testCaseEvaluatorMapper.selectList(any())).thenReturn(List.of(binding));
        EvaluatorVersionEntity version = new EvaluatorVersionEntity();
        version.setId(71L); version.setSpaceId(9L); version.setEvaluatorId(70L); version.setVersionNo(2);
        version.setStatus("PUBLISHED"); version.setEvaluatorKey("artifact-contract");
        when(evaluatorVersionMapper.selectBatchIds(List.of(71L))).thenReturn(List.of(version));
        EvaluatorEntity evaluator = new EvaluatorEntity();
        evaluator.setId(70L); evaluator.setSpaceId(9L); evaluator.setName("归档评估器"); evaluator.setArchived(true);
        when(evaluatorMapper.selectBatchIds(List.of(70L))).thenReturn(List.of(evaluator));

        var result = service.testCaseEvaluators(21L).getFirst();
        assertThat(result.expectedJson()).isEqualTo(binding.getExpectedJson());
        assertThat(result.evaluatorName()).isEqualTo("归档评估器");
        assertThat(result.sortOrder()).isEqualTo(3);
        verify(evaluatorVersionMapper).selectBatchIds(List.of(71L));
        verify(evaluatorMapper).selectBatchIds(List.of(70L));
    }

    @Test
    void foreignSpaceBindingCannotExposeNameOrExpectedConfiguration() {
        EvaluationTestCaseVersionEntity source = new EvaluationTestCaseVersionEntity();
        source.setId(21L); source.setSpaceId(9L);
        when(testCaseVersionMapper.selectById(21L)).thenReturn(source);
        TestCaseEvaluatorEntity binding = new TestCaseEvaluatorEntity();
        binding.setEvaluatorVersionId(71L);
        when(testCaseEvaluatorMapper.selectList(any())).thenReturn(List.of(binding));
        EvaluatorVersionEntity foreign = new EvaluatorVersionEntity();
        foreign.setId(71L); foreign.setSpaceId(8L); foreign.setEvaluatorId(70L);
        when(evaluatorVersionMapper.selectBatchIds(List.of(71L))).thenReturn(List.of(foreign));
        when(evaluatorMapper.selectBatchIds(List.of(70L))).thenReturn(List.of());
        assertThatThrownBy(() -> service.testCaseEvaluators(21L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void searchSqlScopesFiltersInclusiveDatesAndStableOrderingBeforePaging() {
        EvaluationRunSearchParam param = runSearch();
        param.setStatus(EvaluationRunStatus.PAUSED);
        param.setDatasetVersionId(11L);
        param.setSingleTestCaseVersionId(21L);
        param.setExperimentVariantId(30L);
        param.setCreatedFrom(LocalDateTime.of(2026, 9, 28, 0, 0));
        param.setCreatedTo(LocalDateTime.of(2026, 9, 29, 0, 0));
        param.setStartedFrom(param.getCreatedFrom());
        param.setStartedTo(param.getCreatedTo());
        param.setPageNum(2); param.setPageSize(20);
        when(runMapper.selectPage(any(Page.class), any())).thenAnswer(call -> {
            Page<?> page = call.getArgument(0);
            LambdaQueryWrapper<EvaluationRunEntity> query = call.getArgument(1);
            assertThat(page.getCurrent()).isEqualTo(2);
            assertThat(page.getSize()).isEqualTo(20);
            assertThat(query.getSqlSegment()).contains("space_id =", "status =", "dataset_version_id =",
                    "single_test_case_version_id =", "experiment_variant_id =", "created_at >=", "created_at <=",
                    "started_at >=", "started_at <=", "ORDER BY created_at DESC,id DESC");
            assertThat(query.getParamNameValuePairs().values()).contains(9L, "PAUSED", 11L, 21L, 30L,
                    param.getCreatedFrom(), param.getCreatedTo());
            return new Page<EvaluationRunEntity>(2, 20, 0);
        });
        assertThat(service.searchRuns(param).records()).isEmpty();
    }

    @Test
    void experimentSearchDeniesForeignSpaceBeforeSqlAndUsesStableScope() {
        ExperimentSearchParam param = new ExperimentSearchParam();
        param.setSpaceId(9L); param.setStatus(ExperimentStatus.COMPLETED); param.setDatasetVersionId(11L);
        when(experimentMapper.selectPage(any(Page.class), any())).thenAnswer(call -> {
            LambdaQueryWrapper<ExperimentEntity> query = call.getArgument(1);
            assertThat(query.getSqlSegment()).contains("space_id =", "status =", "dataset_version_id =",
                    "ORDER BY created_at DESC,id DESC");
            assertThat(query.getParamNameValuePairs().values()).contains(9L, "COMPLETED", 11L);
            return new Page<ExperimentEntity>(1, 10, 0);
        });
        assertThat(service.searchExperiments(param).records()).isEmpty();
        param.setSpaceId(8L);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权限"))
                .when(spaceAccessService).requirePermission(8L, EVALUATION_READ);
        assertThatThrownBy(() -> service.searchExperiments(param)).isInstanceOf(BusinessException.class);
    }

    private static EvaluationRunSearchParam runSearch() {
        EvaluationRunSearchParam param = new EvaluationRunSearchParam();
        param.setSpaceId(9L);
        return param;
    }

    private static EvaluationRunEntity run(Long id) {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(id);
        run.setSpaceId(9L);
        return run;
    }

    private static EvaluationCaseRunEntity caseRun(Long id, Long runId, String status) {
        EvaluationCaseRunEntity caseRun = new EvaluationCaseRunEntity();
        caseRun.setId(id);
        caseRun.setRunId(runId);
        caseRun.setSpaceId(9L);
        caseRun.setStatus(status);
        return caseRun;
    }

    private static EvaluationDatasetVersionEntity datasetVersion() {
        EvaluationDatasetVersionEntity version = new EvaluationDatasetVersionEntity();
        version.setId(11L);
        version.setDatasetId(10L);
        version.setSpaceId(9L);
        version.setVersionNo(3);
        return version;
    }

    private static EvaluationDatasetEntity dataset() {
        EvaluationDatasetEntity dataset = new EvaluationDatasetEntity();
        dataset.setId(10L);
        dataset.setSpaceId(9L);
        dataset.setName("回归数据集");
        return dataset;
    }

    private static EvaluationCaseAttemptEntity attempt(Long id, int number) {
        EvaluationCaseAttemptEntity attempt = new EvaluationCaseAttemptEntity();
        attempt.setId(id);
        attempt.setAttemptNo(number);
        attempt.setCaseRunId(31L);
        return attempt;
    }

    private static EvaluationResultEntity result(Long id, Long attemptId, Long evaluatorVersionId, int number) {
        EvaluationResultEntity result = new EvaluationResultEntity();
        result.setId(id);
        result.setCaseAttemptId(attemptId);
        result.setEvaluatorVersionId(evaluatorVersionId);
        result.setEvaluationAttemptNo(number);
        return result;
    }

    private static void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode()));
    }
}
