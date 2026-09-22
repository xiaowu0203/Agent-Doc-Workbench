package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
    @Mock private EvaluatorContractValidator evaluatorContractValidator;

    private EvaluationCatalogService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationCatalogService(datasetMapper, datasetVersionMapper, datasetCaseMapper,
                testCaseMapper, testCaseVersionMapper, testCaseEvaluatorMapper, evaluatorMapper,
                evaluatorVersionMapper, spaceAccessService, taskFeign, evaluatorContractValidator);
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
