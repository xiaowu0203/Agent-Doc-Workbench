package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.ReplaySourceVO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.evaluation.evaluator.OnlineOriginalTextEvaluator;
import com.agentdoc.evaluation.evaluator.OnlineOriginalChangeEvaluator;
import com.agentdoc.evaluation.pojo.dto.EvaluatorVersionCreateDTO;
import com.agentdoc.evaluation.pojo.dto.DatasetVersionCreateDTO;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import com.agentdoc.evaluation.evaluator.EvaluatorContractValidator;
import com.agentdoc.evaluation.mapper.EvaluationDatasetCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluatorMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.TestCaseEvaluatorMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.TestCaseEvaluatorEntity;
import com.agentdoc.evaluation.pojo.dto.EvaluatorVersionUpdateDTO;
import com.agentdoc.evaluation.pojo.dto.TestCaseVersionUpdateDTO;
import com.agentdoc.evaluation.pojo.param.EvaluatorPublishedSearchParam;
import com.agentdoc.evaluation.pojo.param.EvaluationResourceSearchParam;
import com.agentdoc.evaluation.pojo.vo.CatalogVersionSummaryVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.agentdoc.common.utils.StableSnapshotUtils;
import java.util.Map;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;

@ExtendWith(MockitoExtension.class)
class EvaluationCatalogServiceTest {

    @Mock private EvaluationDatasetMapper datasetMapper;
    @Mock private EvaluationDatasetVersionMapper datasetVersionMapper;
    @Mock private EvaluationDatasetCaseMapper datasetCaseMapper;
    @Mock private EvaluationTestCaseMapper testCaseMapper;
    @Mock private EvaluationTestCaseVersionMapper testCaseVersionMapper;
    @Mock private TestCaseEvaluatorMapper testCaseEvaluatorMapper;
    @Mock private EvaluatorMapper evaluatorMapper;
    @Mock private EvaluatorVersionMapper evaluatorVersionMapper;
    @Mock private SpaceAccessService spaceAccessService;
    @Mock private TaskFeign taskFeign;
    @Mock private DocumentFeign documentFeign;
    @Mock private EvaluatorContractValidator evaluatorContractValidator;

    private EvaluationCatalogService service;

    private EvaluationTestCaseVersionEntity sourceVersion() {
        var version = new EvaluationTestCaseVersionEntity();
        version.setId(31L); version.setSpaceId(9L); version.setStatus("ARCHIVED");
        version.setSourceTaskId(100L); version.setSourceExecutionId(101L);
        version.setSourceInputSchemaVersion(1); version.setSourceInputHash("input");
        version.setSourceExecutionSchemaVersion(3); version.setSourceExecutionHash("execution");
        version.setDocumentVersionSnapshot(4L); version.setDocumentContentSha256("document");
        return version;
    }

    private ReplaySourceVO sourceProjection(String mismatch) {
        return new ReplaySourceVO(!mismatch.equals("replayable"), null,
                mismatch.equals("task") ? 200L : 100L, mismatch.equals("execution") ? 201L : 101L,
                mismatch.equals("space") ? 8L : 9L, 3L, 100L, 100L, "ORIGINAL", 0,
                mismatch.equals("input-schema") ? 2 : 1, mismatch.equals("input-hash") ? "other" : "input",
                mismatch.equals("execution-schema") ? 4 : 3, mismatch.equals("execution-hash") ? "other" : "execution",
                mismatch.equals("document-id") ? null : 4L, mismatch.equals("document-version") ? 5L : 4L,
                mismatch.equals("document-hash") ? "other" : "document");
    }

    private void stubExperimentPreview() {
        var datasetVersion = new EvaluationDatasetVersionEntity();
        datasetVersion.setId(11L); datasetVersion.setDatasetId(12L); datasetVersion.setSpaceId(9L);
        datasetVersion.setStatus("PUBLISHED");
        when(datasetVersionMapper.selectById(11L)).thenReturn(datasetVersion);
        when(datasetMapper.selectById(12L)).thenReturn(dataset(false));
        var first = new EvaluationDatasetCaseEntity(); first.setTestCaseVersionId(31L);
        var second = new EvaluationDatasetCaseEntity(); second.setTestCaseVersionId(33L);
        when(datasetCaseMapper.selectList(any())).thenReturn(List.of(first, second));
        var version = sourceVersion(); version.setTestCaseId(32L); version.setStatus("PUBLISHED");
        version.setSourceExecutionHash("a".repeat(64));
        var other = sourceVersion(); other.setId(33L); other.setTestCaseId(32L); other.setStatus("PUBLISHED");
        other.setSourceExecutionHash("a".repeat(64));
        when(testCaseVersionMapper.selectBatchIds(any())).thenReturn(List.of(version, other));
        when(testCaseMapper.selectBatchIds(any())).thenReturn(List.of(testCase(false)));
    }

    @Test
    void experimentPreviewBatchesCasesAndReadsOnlyOneRepresentativeWithoutWrites() {
        stubExperimentPreview();
        when(taskFeign.getReplaySource(100L, 9L)).thenReturn(Result.ok(new ReplaySourceVO(true, null,
                100L, 101L, 9L, 3L, 100L, 100L, "ORIGINAL", 0, 1, "input", 3, "a".repeat(64), 4L, 4L, "document")));
        var preview = service.getDatasetExperimentSource(11L);
        assertThat(preview.sourceConsistent()).isTrue();
        assertThat(preview.enabledCaseCount()).isEqualTo(2);
        assertThat(preview.executionSnapshotHash()).isEqualTo("a".repeat(64));
        assertThat(preview.tokenBudget()).isEqualTo(100L);
        verify(testCaseVersionMapper).selectBatchIds(List.of(31L, 33L));
        verify(taskFeign).getReplaySource(100L, 9L);
        verify(datasetVersionMapper, never()).updateById(any(EvaluationDatasetVersionEntity.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"hash", "schema", "space", "archived", "missing"})
    void experimentPreviewRejectsInconsistentCasesBeforeRemoteRead(String kind) {
        stubExperimentPreview();
        var versions = testCaseVersionMapper.selectBatchIds(List.of(31L, 33L));
        var changed = versions.get(1);
        if (kind.equals("hash")) changed.setSourceExecutionHash("b".repeat(64));
        if (kind.equals("schema")) changed.setSourceExecutionSchemaVersion(4);
        if (kind.equals("space")) changed.setSpaceId(8L);
        if (kind.equals("archived")) changed.setStatus("ARCHIVED");
        if (kind.equals("missing")) changed.setSourceTaskId(null);
        var preview = service.getDatasetExperimentSource(11L);
        assertThat(preview.sourceConsistent()).isFalse();
        assertThat(preview.issues()).isNotEmpty();
        assertThat(preview.sourceTaskId()).isNull();
        assertThat(preview.executionSnapshotHash()).isNull();
        verifyNoInteractions(taskFeign, documentFeign);
    }

    @Test
    void experimentPreviewRejectsRepresentativeWhoseFrozenIdentityChanged() {
        stubExperimentPreview();
        when(taskFeign.getReplaySource(100L, 9L)).thenReturn(Result.ok(sourceProjection("")));
        var preview = service.getDatasetExperimentSource(11L);
        assertThat(preview.sourceConsistent()).isFalse();
        assertThat(preview.issues()).containsExactly("BASELINE_SOURCE_UNAVAILABLE");
        assertThat(preview.agentId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {EVALUATION_READ, TASK_READ})
    void experimentPreviewDeniesBeforeBindingsAndRemoteReads(String permission) {
        var datasetVersion = new EvaluationDatasetVersionEntity(); datasetVersion.setSpaceId(9L);
        when(datasetVersionMapper.selectById(11L)).thenReturn(datasetVersion);
        doAnswer(call -> { if (permission.equals(call.getArgument(1))) throw new BusinessException(403, "denied"); return null; })
                .when(spaceAccessService).requirePermission(eq(9L), anyString());
        assertThatThrownBy(() -> service.getDatasetExperimentSource(11L)).hasMessageContaining("denied");
        verifyNoInteractions(datasetCaseMapper, testCaseVersionMapper, taskFeign, documentFeign);
    }

    @Test
    void sourceTitleIsReadOnlyAndMatchesArchivedVersionFrozenIdentity() {
        var version = sourceVersion();
        when(testCaseVersionMapper.selectById(31L)).thenReturn(version);
        when(taskFeign.getReplaySource(100L, 9L)).thenReturn(Result.ok(sourceProjection("")));
        when(documentFeign.getDocumentRefs(List.of(4L))).thenReturn(Result.ok(List.of(new DocumentRefVO(4L, 9L, "当前文档"))));
        var result = service.getTestCaseSource(31L);
        assertThat(result.testCaseVersionId()).isEqualTo(31L);
        assertThat(result.documentId()).isEqualTo(4L);
        assertThat(result.documentTitle()).isEqualTo("当前文档");
        assertThat(result.sourceInputHash()).isEqualTo("input");
        verify(spaceAccessService).requirePermission(9L, EVALUATION_READ);
        verify(spaceAccessService).requirePermission(9L, TASK_READ);
        verify(spaceAccessService).requirePermission(9L, DOCUMENT_READ);
        verify(testCaseVersionMapper, never()).updateById(any(EvaluationTestCaseVersionEntity.class));
        assertThat(version.getStatus()).isEqualTo("ARCHIVED");
        assertThat(version.getDocumentVersionSnapshot()).isEqualTo(4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"task", "execution", "space", "input-schema", "input-hash", "execution-schema", "execution-hash", "document-id", "document-version", "document-hash", "replayable"})
    void sourceMismatchNeverQueriesDocumentNames(String mismatch) {
        when(testCaseVersionMapper.selectById(31L)).thenReturn(sourceVersion());
        when(taskFeign.getReplaySource(100L, 9L)).thenReturn(Result.ok(sourceProjection(mismatch)));
        assertThatThrownBy(() -> service.getTestCaseSource(31L)).isInstanceOf(BusinessException.class).hasMessageContaining("冻结依据");
        verifyNoInteractions(documentFeign);
    }

    @ParameterizedTest
    @ValueSource(strings = {EVALUATION_READ, TASK_READ, DOCUMENT_READ})
    void sourceMetadataDeniesBeforeRemoteReads(String permission) {
        when(testCaseVersionMapper.selectById(31L)).thenReturn(sourceVersion());
        doAnswer(call -> {
            if (permission.equals(call.getArgument(1))) { throw new BusinessException(403, "denied"); }
            return null;
        }).when(spaceAccessService).requirePermission(eq(9L), anyString());
        assertThatThrownBy(() -> service.getTestCaseSource(31L)).hasMessageContaining("denied");
        verifyNoInteractions(taskFeign, documentFeign);
    }

    @Test
    void missingOrForeignDocumentTitleCannotLeakIntoSource() {
        when(testCaseVersionMapper.selectById(31L)).thenReturn(sourceVersion());
        when(taskFeign.getReplaySource(100L, 9L)).thenReturn(Result.ok(sourceProjection("")));
        when(documentFeign.getDocumentRefs(List.of(4L))).thenReturn(Result.ok(List.of(new DocumentRefVO(4L, 8L, "其他空间"), new DocumentRefVO(5L, 9L, "其他文档"))));
        assertThat(service.getTestCaseSource(31L).documentTitle()).isNull();
        when(documentFeign.getDocumentRefs(List.of(4L))).thenReturn(Result.ok(List.of()));
        assertThat(service.getTestCaseSource(31L).documentId()).isEqualTo(4L);
        assertThat(service.getTestCaseSource(31L).documentTitle()).isNull();
    }

    @Test
    void publishedBatchChecksAllParentsWithTwoQueries() {
        var version = new EvaluatorVersionEntity();
        version.setId(41L); version.setEvaluatorId(42L); version.setSpaceId(9L); version.setStatus("PUBLISHED");
        version.setEvaluatorKey("artifact-contract"); version.setConfigSchemaVersion(1); version.setResultSchemaVersion(1);
        version.setConfigJson("{}"); version.setImplementationVersion("test-v1");
        version.setContentHash(StableSnapshotUtils.snapshotHash(1, Map.of("evaluatorKey", "artifact-contract", "configSchemaVersion", 1,
                "config", Map.of(), "resultSchemaVersion", 1, "implementationVersion", "test-v1")));
        when(evaluatorVersionMapper.selectBatchIds(any())).thenReturn(List.of(version));
        when(evaluatorMapper.selectBatchIds(any())).thenReturn(List.of(evaluator(false)));
        var query = new EvaluatorPublishedSearchParam(); query.setSpaceId("9"); query.setVersionIds(List.of("41"));
        assertThat(service.publishedEvaluatorVersions(query)).hasSize(1);
        verify(evaluatorVersionMapper).selectBatchIds(List.of(41L));
        verify(evaluatorMapper).selectBatchIds(List.of(42L));
        when(evaluatorMapper.selectBatchIds(any())).thenReturn(List.of(evaluator(true)));
        assertThatThrownBy(() -> service.publishedEvaluatorVersions(query)).hasMessageContaining("失效");
    }

    @Test
    void publishedBatchRejectsDuplicateIdentityBeforeQueryAndDeniedReadBeforeRows() {
        var query = new EvaluatorPublishedSearchParam(); query.setSpaceId("9"); query.setVersionIds(List.of("41", "41"));
        assertThatThrownBy(() -> service.publishedEvaluatorVersions(query)).isInstanceOf(BusinessException.class);
        verify(evaluatorVersionMapper, never()).selectBatchIds(any());
        query.setVersionIds(List.of("41"));
        doThrow(new BusinessException(403, "denied")).when(spaceAccessService)
                .requirePermission(9L, EVALUATION_READ);
        assertThatThrownBy(() -> service.publishedEvaluatorVersions(query)).hasMessageContaining("denied");
        verify(evaluatorVersionMapper, never()).selectBatchIds(any());
    }

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "catalog-test");
        TableInfoHelper.initTableInfo(assistant, EvaluatorVersionEntity.class);
        TableInfoHelper.initTableInfo(assistant, EvaluationDatasetEntity.class);
        TableInfoHelper.initTableInfo(assistant, EvaluationDatasetVersionEntity.class);
        TableInfoHelper.initTableInfo(assistant, EvaluationDatasetCaseEntity.class);
        TableInfoHelper.initTableInfo(assistant, EvaluationTestCaseEntity.class);
        TableInfoHelper.initTableInfo(assistant, EvaluatorEntity.class);
        TableInfoHelper.initTableInfo(assistant, EvaluationTestCaseVersionEntity.class);
        service = new EvaluationCatalogService(datasetMapper, datasetVersionMapper, datasetCaseMapper,
                testCaseMapper, testCaseVersionMapper, testCaseEvaluatorMapper, evaluatorMapper,
                evaluatorVersionMapper, spaceAccessService, taskFeign, documentFeign, evaluatorContractValidator);
    }

    @Test
    void catalogPageBatchesOnlyCurrentResourcesAndKeepsZeroSeparateFromNoPublishedVersion() {
        var one = new EvaluationDatasetEntity(); one.setId(21L); one.setSpaceId(9L);
        var two = new EvaluationDatasetEntity(); two.setId(22L); two.setSpaceId(9L);
        var summary = new CatalogVersionSummaryVO(21L, 5L, 0L, 31L, 4, 0L);
        when(datasetMapper.selectPage(any(Page.class), any())).thenReturn(new Page<EvaluationDatasetEntity>(2, 10).setRecords(List.of(one, two)).setTotal(12));
        when(datasetVersionMapper.resourceSummaries(9L, List.of(21L, 22L))).thenReturn(List.of(summary));
        var param = new EvaluationResourceSearchParam(); param.setSpaceId(9L); param.setPageNum(2);
        var page = service.searchDatasets(param);
        assertThat(page.records().getFirst().versionSummary()).isEqualTo(summary);
        assertThat(page.records().getLast().versionSummary()).isEqualTo(CatalogVersionSummaryVO.empty(22L));
        assertThat(page.total()).isEqualTo(12);
        verify(datasetVersionMapper).resourceSummaries(9L, List.of(21L, 22L));
        verify(datasetVersionMapper, never()).selectList(any());
        verifyNoInteractions(datasetCaseMapper, taskFeign);
    }

    @Test
    void emptyCatalogPageDoesNotQueryVersionsAndDeniedReadDoesNotQueryResources() {
        var param = new EvaluationResourceSearchParam(); param.setSpaceId(9L);
        when(evaluatorMapper.selectPage(any(Page.class), any())).thenReturn(new Page<EvaluatorEntity>(1, 10));
        assertThat(service.searchEvaluators(param).records()).isEmpty();
        verifyNoInteractions(evaluatorVersionMapper);
        doThrow(new BusinessException(403, "denied")).when(spaceAccessService).requirePermission(9L, EVALUATION_READ);
        assertThatThrownBy(() -> service.searchTestCases(param)).hasMessageContaining("denied");
        verifyNoInteractions(testCaseMapper, testCaseVersionMapper);
    }

    @Test
    void updatesDraftExpectedWithoutChangingFrozenSourceAndClearsNote() {
        EvaluationTestCaseVersionEntity version = draftCase();
        version.setSourceTaskId(88L);
        version.setSourceInputHash("frozen-input");
        version.setSourceExecutionHash("frozen-execution");
        when(testCaseVersionMapper.selectById(31L)).thenReturn(version);
        when(testCaseMapper.selectById(32L)).thenReturn(testCase(false));
        when(testCaseVersionMapper.update(isNull(), any())).thenReturn(1);

        var result = service.updateTestCaseVersion(31L, new TestCaseVersionUpdateDTO(1, "{}", "LIVE", null));
        assertThat(result.sourceTaskId()).isEqualTo(88L);
        assertThat(result.sourceInputHash()).isEqualTo("frozen-input");
        assertThat(result.sourceExecutionHash()).isEqualTo("frozen-execution");
        assertThat(result.sanitizationNote()).isNull();
        verify(spaceAccessService).requirePermission(9L, EVALUATION_MANAGE);
        verify(taskFeign, never()).getReplaySource(any(), any());
    }

    @Test
    void updatesEvaluatorDraftWithoutChangingImplementation() {
        EvaluatorVersionEntity version = draftEvaluator();
        when(evaluatorVersionMapper.selectById(41L)).thenReturn(version);
        when(evaluatorMapper.selectById(42L)).thenReturn(evaluator(false));
        when(evaluatorVersionMapper.update(isNull(), any())).thenReturn(1);
        var result = service.updateEvaluatorVersion(41L, new EvaluatorVersionUpdateDTO(1, "{}", 1));
        assertThat(result.implementationVersion()).isEqualTo("phase3-v1");
        verify(spaceAccessService).requirePermission(9L, EVALUATION_MANAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"online-original-text-assertion", "online-document-change-validator"})
    void newOriginalVersionUsesImplementedEngineWithoutChangingPublishedContractOnlyVersion(String key) {
        var parent = evaluator(false); parent.setEvaluatorKey(key);
        when(evaluatorMapper.selectById(42L)).thenReturn(parent);
        var prior = draftEvaluator(); prior.setStatus("PUBLISHED"); prior.setVersionNo(7); prior.setImplementationVersion("online-contract-v2");
        when(evaluatorVersionMapper.selectOne(any())).thenReturn(prior);
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(501L);
            var result = service.createEvaluatorVersion(new EvaluatorVersionCreateDTO(42L,1,"{}",1));
            assertThat(result.implementationVersion()).isEqualTo("online-original-text-assertion".equals(key)
                    ? OnlineOriginalTextEvaluator.IMPLEMENTATION_VERSION : OnlineOriginalChangeEvaluator.IMPLEMENTATION_VERSION);
            assertThat(result.versionNo()).isEqualTo(8); assertThat(prior.getImplementationVersion()).isEqualTo("online-contract-v2");
        }
        verify(evaluatorVersionMapper,never()).updateById(any(EvaluatorVersionEntity.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "ARCHIVED"})
    void frozenVersionsRejectUpdates(String status) {
        var caseVersion = draftCase();
        caseVersion.setStatus(status);
        when(testCaseVersionMapper.selectById(31L)).thenReturn(caseVersion);
        assertThatThrownBy(() -> service.updateTestCaseVersion(31L,
                new TestCaseVersionUpdateDTO(1, "{}", "LIVE", null))).isInstanceOf(BusinessException.class);
        var evaluatorVersion = draftEvaluator();
        evaluatorVersion.setStatus(status);
        when(evaluatorVersionMapper.selectById(41L)).thenReturn(evaluatorVersion);
        when(evaluatorMapper.selectById(42L)).thenReturn(evaluator(false));
        assertThatThrownBy(() -> service.updateEvaluatorVersion(41L,
                new EvaluatorVersionUpdateDTO(1, "{}", 1))).isInstanceOf(BusinessException.class);
        verify(testCaseVersionMapper, never()).update(isNull(), any());
        verify(evaluatorVersionMapper, never()).update(isNull(), any());
    }

    @Test
    void deniedManagerCannotWriteDraft() {
        when(testCaseVersionMapper.selectById(31L)).thenReturn(draftCase());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(spaceAccessService)
                .requirePermission(9L, EVALUATION_MANAGE);
        assertThatThrownBy(() -> service.updateTestCaseVersion(31L,
                new TestCaseVersionUpdateDTO(1, "{}", "LIVE", null))).isInstanceOf(BusinessException.class);
        verify(testCaseVersionMapper, never()).update(isNull(), any());
    }

    @Test
    void rejectsConcurrentPublishInsteadOfWritingFrozenEvaluator() {
        when(evaluatorVersionMapper.selectById(41L)).thenReturn(draftEvaluator());
        when(evaluatorMapper.selectById(42L)).thenReturn(evaluator(false));
        when(evaluatorVersionMapper.update(isNull(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.updateEvaluatorVersion(41L,
                new EvaluatorVersionUpdateDTO(1, "{}", 1))).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsInvalidJsonBeforeWritingDraft() {
        when(evaluatorVersionMapper.selectById(41L)).thenReturn(draftEvaluator());
        when(evaluatorMapper.selectById(42L)).thenReturn(evaluator(false));
        assertThatThrownBy(() -> service.updateEvaluatorVersion(41L,
                new EvaluatorVersionUpdateDTO(1, "invalid", 1))).isInstanceOf(RuntimeException.class);
        verify(evaluatorVersionMapper, never()).update(isNull(), any());
    }

    private static EvaluationTestCaseVersionEntity draftCase() {
        var version = new EvaluationTestCaseVersionEntity();
        version.setId(31L);
        version.setTestCaseId(32L);
        version.setSpaceId(9L);
        version.setStatus("DRAFT");
        return version;
    }

    private static EvaluatorVersionEntity draftEvaluator() {
        var version = new EvaluatorVersionEntity();
        version.setId(41L);
        version.setEvaluatorId(42L);
        version.setSpaceId(9L);
        version.setEvaluatorKey("artifact-contract");
        version.setStatus("DRAFT");
        version.setImplementationVersion("phase3-v1");
        return version;
    }

    @Test
    void refusesDatasetPublishWhenBoundStableTestCaseWasArchived() {
        EvaluationDatasetVersionEntity version = datasetVersion();
        when(datasetVersionMapper.selectById(11L)).thenReturn(version);
        when(datasetMapper.selectById(1L)).thenReturn(dataset(false));
        EvaluationDatasetCaseEntity binding = new EvaluationDatasetCaseEntity();
        binding.setTestCaseVersionId(21L);
        binding.setEnabled(true);
        binding.setSortOrder(1);
        when(datasetCaseMapper.selectList(any())).thenReturn(List.of(binding));
        EvaluationTestCaseVersionEntity caseVersion = new EvaluationTestCaseVersionEntity();
        caseVersion.setId(21L);
        caseVersion.setTestCaseId(22L);
        caseVersion.setSpaceId(9L);
        caseVersion.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        when(testCaseVersionMapper.selectBatchIds(any())).thenReturn(List.of(caseVersion));
        when(testCaseMapper.selectBatchIds(any())).thenReturn(List.of(testCase(true)));

        assertThatThrownBy(() -> service.publishDatasetVersion(11L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.CONFLICT.getCode()));
    }

    @Test
    void refusesTestCasePublishWhenBoundStableEvaluatorWasArchived() {
        EvaluationTestCaseVersionEntity version = new EvaluationTestCaseVersionEntity();
        version.setId(31L);
        version.setTestCaseId(32L);
        version.setSpaceId(9L);
        version.setStatus(EvaluationVersionStatus.DRAFT.name());
        version.setExpectedJson("{}");
        when(testCaseVersionMapper.selectById(31L)).thenReturn(version);
        when(testCaseMapper.selectById(32L)).thenReturn(testCase(false));
        TestCaseEvaluatorEntity binding = new TestCaseEvaluatorEntity();
        binding.setEvaluatorVersionId(41L);
        when(testCaseEvaluatorMapper.selectList(any())).thenReturn(List.of(binding));
        EvaluatorVersionEntity evaluatorVersion = new EvaluatorVersionEntity();
        evaluatorVersion.setId(41L);
        evaluatorVersion.setEvaluatorId(42L);
        evaluatorVersion.setSpaceId(9L);
        evaluatorVersion.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        evaluatorVersion.setEvaluatorKey("artifact-contract");
        when(evaluatorVersionMapper.selectBatchIds(any())).thenReturn(List.of(evaluatorVersion));
        when(evaluatorMapper.selectBatchIds(any())).thenReturn(List.of(evaluator(true)));

        assertThatThrownBy(() -> service.publishTestCaseVersion(31L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.CONFLICT.getCode()));
    }

    @Test
    void refusesEvaluatorVersionPublishAfterStableEvaluatorArchive() {
        EvaluatorVersionEntity version = new EvaluatorVersionEntity();
        version.setId(41L);
        version.setEvaluatorId(42L);
        version.setSpaceId(9L);
        version.setStatus(EvaluationVersionStatus.DRAFT.name());
        version.setEvaluatorKey("artifact-contract");
        when(evaluatorVersionMapper.selectById(41L)).thenReturn(version);
        when(evaluatorMapper.selectById(42L)).thenReturn(evaluator(true));

        assertThatThrownBy(() -> service.publishEvaluatorVersion(41L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void explicitCopyPreservesDisabledBindingsAndOrderWithoutChangingPublishedSource() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset(false));
        var source = datasetVersion(); source.setStatus("PUBLISHED"); source.setVersionNo(3);
        source.setContentHash("frozen-hash");
        when(datasetVersionMapper.selectById(11L)).thenReturn(source);
        when(datasetVersionMapper.selectOne(any())).thenReturn(source);
        var first = new EvaluationDatasetCaseEntity(); first.setId(51L); first.setTestCaseVersionId(31L);
        first.setSortOrder(7); first.setEnabled(false);
        var second = new EvaluationDatasetCaseEntity(); second.setId(52L); second.setTestCaseVersionId(33L);
        second.setSortOrder(12); second.setEnabled(true);
        when(datasetCaseMapper.selectList(any())).thenReturn(List.of(first, second));
        var one = sourceVersion(); one.setStatus("PUBLISHED"); one.setTestCaseId(32L);
        var two = sourceVersion(); two.setId(33L); two.setStatus("PUBLISHED"); two.setTestCaseId(32L);
        when(testCaseVersionMapper.selectBatchIds(List.of(31L, 33L))).thenReturn(List.of(one, two));
        when(testCaseMapper.selectBatchIds(any())).thenReturn(List.of(testCase(false)));
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(5L);
            var created = service.createDatasetVersion(new DatasetVersionCreateDTO(1L, 11L));
            assertThat(created.status()).isEqualTo("DRAFT");
            assertThat(created.versionNo()).isEqualTo(4);
            assertThat(created.contentHash()).isNull();
            verify(datasetCaseMapper).insertBatch(argThat(copies -> {
                assertThat(copies).hasSize(2);
                assertThat(copies).allMatch(copy -> copy.getDatasetVersionId().equals(created.id()));
                assertThat(copies.getFirst().getId()).isNotEqualTo(first.getId());
                assertThat(copies.getFirst().getSortOrder()).isEqualTo(7);
                assertThat(copies.getFirst().getEnabled()).isFalse();
                assertThat(copies.getLast().getSortOrder()).isEqualTo(12);
                assertThat(copies.getLast().getEnabled()).isTrue();
                return true;
            }));
        }
        assertThat(source.getStatus()).isEqualTo("PUBLISHED");
        assertThat(source.getContentHash()).isEqualTo("frozen-hash");
        verify(datasetVersionMapper, never()).updateById(any(EvaluationDatasetVersionEntity.class));
        verifyNoInteractions(taskFeign, documentFeign);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "ARCHIVED", "parent", "space", "missing"})
    void copyRejectsUnavailableOrUnrelatedSourceBeforeWriting(String kind) {
        when(datasetMapper.selectById(1L)).thenReturn(dataset(false));
        var source = datasetVersion(); source.setStatus("PUBLISHED");
        if (kind.equals("DRAFT") || kind.equals("ARCHIVED")) source.setStatus(kind);
        if (kind.equals("parent")) source.setDatasetId(2L);
        if (kind.equals("space")) source.setSpaceId(8L);
        when(datasetVersionMapper.selectById(11L)).thenReturn(kind.equals("missing") ? null : source);
        assertThatThrownBy(() -> service.createDatasetVersion(new DatasetVersionCreateDTO(1L, 11L)))
                .hasMessageContaining("当前数据集已发布版本");
        verify(datasetVersionMapper, never()).insert(any(EvaluationDatasetVersionEntity.class));
        verifyNoInteractions(datasetCaseMapper, testCaseVersionMapper);
    }

    @Test
    void emptyDraftDoesNotReadOrCopyOtherVersionBindings() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset(false));
        try (var auth = mockStatic(AuthUtils.class)) {
            auth.when(AuthUtils::getUserIdOrException).thenReturn(5L);
            assertThat(service.createDatasetVersion(new DatasetVersionCreateDTO(1L, null)).status()).isEqualTo("DRAFT");
        }
        verifyNoInteractions(datasetCaseMapper, testCaseVersionMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version-archived", "parent-archived", "parent-space", "case-space", "missing"})
    void copyRejectsInvalidDependenciesIncludingDisabledBindingsWithoutPartialInsert(String kind) {
        when(datasetMapper.selectById(1L)).thenReturn(dataset(false));
        var source = datasetVersion(); source.setStatus("PUBLISHED");
        when(datasetVersionMapper.selectById(11L)).thenReturn(source);
        var binding = new EvaluationDatasetCaseEntity(); binding.setTestCaseVersionId(31L);
        binding.setSortOrder(1); binding.setEnabled(false);
        when(datasetCaseMapper.selectList(any())).thenReturn(List.of(binding));
        var child = sourceVersion(); child.setTestCaseId(32L); child.setStatus("PUBLISHED");
        if (kind.equals("version-archived")) child.setStatus("ARCHIVED");
        if (kind.equals("case-space")) child.setSpaceId(8L);
        when(testCaseVersionMapper.selectBatchIds(any())).thenReturn(kind.equals("missing") ? List.of() : List.of(child));
        var parent = testCase(false);
        parent.setArchived(kind.equals("parent-archived"));
        if (kind.equals("parent-space")) parent.setSpaceId(8L);
        when(testCaseMapper.selectBatchIds(any())).thenReturn(List.of(parent));
        assertThatThrownBy(() -> service.createDatasetVersion(new DatasetVersionCreateDTO(1L, 11L)))
                .hasMessageContaining("同空间已发布");
        verify(datasetVersionMapper, never()).insert(any(EvaluationDatasetVersionEntity.class));
        verify(datasetCaseMapper, never()).insertBatch(any());
    }

    @Test
    void copyDeniesBeforeReadingSourceWithoutManagePermission() {
        when(datasetMapper.selectById(1L)).thenReturn(dataset(false));
        doThrow(new BusinessException(403, "denied")).when(spaceAccessService).requirePermission(9L, EVALUATION_MANAGE);
        assertThatThrownBy(() -> service.createDatasetVersion(new DatasetVersionCreateDTO(1L, 11L))).hasMessage("denied");
        verifyNoInteractions(datasetVersionMapper, datasetCaseMapper);
    }

    private static EvaluationDatasetVersionEntity datasetVersion() {
        EvaluationDatasetVersionEntity version = new EvaluationDatasetVersionEntity();
        version.setId(11L);
        version.setDatasetId(1L);
        version.setSpaceId(9L);
        version.setStatus(EvaluationVersionStatus.DRAFT.name());
        return version;
    }

    private static EvaluationDatasetEntity dataset(boolean archived) {
        EvaluationDatasetEntity dataset = new EvaluationDatasetEntity();
        dataset.setId(1L);
        dataset.setSpaceId(9L);
        dataset.setArchived(archived);
        return dataset;
    }

    private static EvaluationTestCaseEntity testCase(boolean archived) {
        EvaluationTestCaseEntity testCase = new EvaluationTestCaseEntity();
        testCase.setId(archived ? 22L : 32L);
        testCase.setSpaceId(9L);
        testCase.setArchived(archived);
        return testCase;
    }

    private static EvaluatorEntity evaluator(boolean archived) {
        EvaluatorEntity evaluator = new EvaluatorEntity();
        evaluator.setId(42L);
        evaluator.setSpaceId(9L);
        evaluator.setEvaluatorKey("artifact-contract");
        evaluator.setArchived(archived);
        return evaluator;
    }
}
