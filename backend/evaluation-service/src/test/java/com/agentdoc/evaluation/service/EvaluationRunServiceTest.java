package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityRenewDTO;
import com.agentdoc.common.feign.dto.ExperimentBatchCreateDTO;
import com.agentdoc.common.feign.dto.ReplayBatchCreateDTO;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangeEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.feign.vo.EvaluationWorkerCapabilityVO;
import com.agentdoc.common.feign.vo.EvaluationTaskCancelVO;
import com.agentdoc.common.feign.vo.ExperimentBatchCreateVO;
import com.agentdoc.common.feign.vo.ExperimentBatchItemVO;
import com.agentdoc.common.feign.vo.ReplayBatchCreateVO;
import com.agentdoc.common.feign.vo.ReplayBatchItemVO;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationPauseReason;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import com.agentdoc.evaluation.config.EvaluationRuntimeProperties;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetCaseMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluationDatasetMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseMapper;
import com.agentdoc.evaluation.mapper.ExperimentVariantMapper;
import com.agentdoc.evaluation.pojo.dto.EvaluationRunCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationRunResumeDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationRetryDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationWorkerCapabilitySegmentEntity;
import com.agentdoc.evaluation.pojo.entity.ExperimentVariantEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationRunServiceTest {

    @Mock private EvaluationDatasetVersionMapper datasetVersionMapper;
    @Mock private EvaluationDatasetMapper datasetMapper;
    @Mock private EvaluationDatasetCaseMapper datasetCaseMapper;
    @Mock private EvaluationTestCaseVersionMapper testCaseVersionMapper;
    @Mock private EvaluationTestCaseMapper testCaseMapper;
    @Mock private ExperimentVariantMapper variantMapper;
    @Mock private EvaluationRunMapper runMapper;
    @Mock private EvaluationFeedbackMapper feedbackMapper;
    @Mock private EvaluationCaseRunMapper caseRunMapper;
    @Mock private EvaluationCaseAttemptMapper attemptMapper;
    @Mock private EvaluationRunPersistenceService persistenceService;
    @Mock private SpaceAccessService spaceAccessService;
    @Mock private TaskFeign taskFeign;
    @Mock private WorkerCapabilitySegmentService segmentService;
    @Mock private EvaluationRunProcessor runProcessor;

    private EvaluationRunService service;
    private EvaluationRuntimeProperties runtimeProperties;

    @BeforeEach
    void setUp() {
        runtimeProperties = new EvaluationRuntimeProperties();
        service = new EvaluationRunService(datasetVersionMapper, datasetMapper, datasetCaseMapper,
                testCaseVersionMapper, testCaseMapper, variantMapper,
                runMapper, feedbackMapper, caseRunMapper, attemptMapper, persistenceService, spaceAccessService, taskFeign,
                segmentService, runProcessor, runtimeProperties);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("501")
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsSingleCaseRunThroughOneBatchReplayCall() {
        EvaluationTestCaseVersionEntity version = publishedCase();
        EvaluationRunPersistenceService.RunDraft draft = draft();
        when(testCaseVersionMapper.selectById(31L)).thenReturn(version);
        when(testCaseMapper.selectById(32L)).thenReturn(activeTestCase());
        when(persistenceService.create(eq(9L), eq(null), eq(31L), eq(501L), eq(List.of(31L))))
                .thenReturn(draft);
        when(taskFeign.createReplayBatch(any())).thenReturn(Result.ok(new ReplayBatchCreateVO(
                71L, 9L, List.of(new ReplayBatchItemVO(99L, "evaluation-case-attempt:73", 801L, "PENDING")),
                "a".repeat(64), "worker-token", Instant.now().plusSeconds(600))));
        draft.run().setStatus(EvaluationRunStatus.RUNNING.name());
        draft.cases().getFirst().attempt().setReplayTaskId(801L);
        draft.cases().getFirst().attempt().setExecutionTaskId(801L);
        draft.cases().getFirst().attempt().setStatus(EvaluationAttemptStatus.REPLAY_CREATED.name());
        draft.cases().getFirst().caseRun().setStatus(EvaluationAttemptStatus.REPLAY_CREATED.name());
        when(persistenceService.attachDispatch(eq(draft), any())).thenReturn(draft);

        var result = service.create(new EvaluationRunCreateDTO(9L, null, 31L, 600L));

        assertThat(result.status()).isEqualTo(EvaluationRunStatus.RUNNING.name());
        assertThat(result.cases()).singleElement().satisfies(item -> assertThat(item.executionTaskId()).isEqualTo(801L));
        ArgumentCaptor<ReplayBatchCreateDTO> request = ArgumentCaptor.forClass(ReplayBatchCreateDTO.class);
        verify(taskFeign).createReplayBatch(request.capture());
        assertThat(request.getValue().items()).singleElement().satisfies(item -> {
            assertThat(item.sourceTaskId()).isEqualTo(99L);
            assertThat(item.derivationRequestKey()).isEqualTo("evaluation-case-attempt:73");
        });
    }

    @Test
    void pausesRunWhenBatchReplayDispatchIsUnavailable() {
        EvaluationRunPersistenceService.RunDraft draft = draft();
        when(testCaseVersionMapper.selectById(31L)).thenReturn(publishedCase());
        when(testCaseMapper.selectById(32L)).thenReturn(activeTestCase());
        when(persistenceService.create(any(), any(), any(), any(), any())).thenReturn(draft);
        when(taskFeign.createReplayBatch(any())).thenReturn(Result.fail(ErrorCode.INTERNAL_ERROR));
        when(persistenceService.pauseDispatch(draft)).thenAnswer(invocation -> {
            draft.run().setStatus(EvaluationRunStatus.PAUSED.name());
            draft.run().setPauseReason(EvaluationPauseReason.DISPATCH_UNAVAILABLE.name());
            return draft;
        });

        var result = service.create(new EvaluationRunCreateDTO(9L, null, 31L, 600L));

        assertThat(result.status()).isEqualTo(EvaluationRunStatus.PAUSED.name());
        assertThat(result.pauseReason()).isEqualTo(EvaluationPauseReason.DISPATCH_UNAVAILABLE.name());
        verify(persistenceService).pauseDispatch(draft);
    }

    @Test
    void rejectsNewRunWhenEmergencyCreationGateIsDisabled() {
        runtimeProperties.setRunCreationEnabled(false);

        assertThatThrownBy(() -> service.create(new EvaluationRunCreateDTO(9L, null, 31L, 600L)))
                .isInstanceOfSatisfying(com.agentdoc.common.exception.BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode()));
    }

    @Test
    void retryEvaluationRenewsCapabilityForSameReplayBeforeReadingEvidence() {
        EvaluationRunPersistenceService.RunDraft existing = draft();
        EvaluationRunEntity run = existing.run();
        run.setStatus(EvaluationRunStatus.COMPLETED.name());
        EvaluationCaseRunEntity caseRun = existing.cases().getFirst().caseRun();
        EvaluationCaseAttemptEntity attempt = existing.cases().getFirst().attempt();
        attempt.setReplayTaskId(801L);
        attempt.setExecutionTaskId(801L);
        attempt.setCapabilitySegmentId(81L);
        attempt.setStatus(EvaluationAttemptStatus.COMPLETED.name());
        when(attemptMapper.selectById(73L)).thenReturn(attempt);
        when(caseRunMapper.selectById(72L)).thenReturn(caseRun);
        when(runMapper.selectById(71L)).thenReturn(run);
        Instant expiresAt = Instant.now().plusSeconds(600);
        String taskIdsHash = StableSnapshotUtils.snapshotHash(1, List.of(801L));
        when(taskFeign.renewEvaluationWorkerCapability(any())).thenReturn(Result.ok(
                new EvaluationWorkerCapabilityVO(71L, 9L, taskIdsHash, "renewed-token", expiresAt)));
        when(segmentService.nextBatchNo(71L)).thenReturn(2);
        EvaluationWorkerCapabilitySegmentEntity segment = new EvaluationWorkerCapabilitySegmentEntity();
        segment.setId(82L);
        when(segmentService.append(71L, 9L, 2, taskIdsHash, "renewed-token", expiresAt))
                .thenReturn(segment);
        when(attemptMapper.selectList(any())).thenReturn(List.of(attempt));
        when(segmentService.requireActiveCapability(82L, 71L, 9L)).thenReturn("renewed-token");
        EvaluationEvidenceBundleVO evidence = new EvaluationEvidenceBundleVO(801L, 9L, 3,
                "COMPLETED", "done", 901L, "COMPLETED", "trace", "span",
                null, null, 10L, 0L, 4L, new BigDecimal("0.01"), "USD",
                0, 1, 0, 0, 0, List.of());
        when(taskFeign.queryEvaluationEvidence(eq("renewed-token"), any()))
                .thenReturn(Result.ok(List.of(evidence)));
        when(runProcessor.requiresDocumentChangeEvidence(31L, java.util.Set.of(41L))).thenReturn(true);
        EvaluationDocumentChangeEvidenceVO documentChange = new EvaluationDocumentChangeEvidenceVO(
                801L, 902L, "a".repeat(64), true, false, "b".repeat(64), null);
        when(taskFeign.queryEvaluationDocumentChanges(eq("renewed-token"), any()))
                .thenReturn(Result.ok(List.of(documentChange)));
        when(caseRunMapper.selectList(any())).thenReturn(List.of(caseRun));
        when(attemptMapper.selectBatchIds(any())).thenReturn(List.of(attempt));
        when(feedbackMapper.selectList(any())).thenReturn(List.of());

        service.retryEvaluation(73L, new EvaluationRetryDTO(List.of(41L), 600L));

        ArgumentCaptor<EvaluationWorkerCapabilityRenewDTO> renew =
                ArgumentCaptor.forClass(EvaluationWorkerCapabilityRenewDTO.class);
        verify(taskFeign).renewEvaluationWorkerCapability(renew.capture());
        assertThat(renew.getValue().taskIds()).containsExactly(801L);
        assertThat(attempt.getCapabilitySegmentId()).isEqualTo(82L);
        verify(runProcessor).retryEvaluation(73L, evidence, List.of(documentChange), java.util.Set.of(41L));
    }

    @Test
    void resumesCandidateWithOriginalExperimentDerivationKey() {
        EvaluationRunPersistenceService.RunDraft draft = pausedExperimentDraft();
        ExperimentVariantEntity variant = experimentVariant("CANDIDATE");
        stubPausedExperiment(draft, variant);
        String key = ExperimentService.derivationKey(91L, 88L, 31L, 1);
        when(taskFeign.createExperimentBatch(any())).thenReturn(Result.ok(new ExperimentBatchCreateVO(
                71L, 88L, 9L, List.of(new ExperimentBatchItemVO(99L, 31L, 1, key, 801L, "FAILED")),
                "a".repeat(64), "worker-token", Instant.now().plusSeconds(600))));
        when(persistenceService.attachExperimentDispatch(any(), any(), anyMap(), eq(2)))
                .thenAnswer(invocation -> {
                    draft.run().setStatus(EvaluationRunStatus.RUNNING.name());
                    return draft;
                });

        service.resume(71L, new EvaluationRunResumeDTO(600L));

        ArgumentCaptor<ExperimentBatchCreateDTO> request =
                ArgumentCaptor.forClass(ExperimentBatchCreateDTO.class);
        verify(taskFeign).createExperimentBatch(request.capture());
        assertThat(request.getValue().candidateConfigId()).isEqualTo(89L);
        assertThat(request.getValue().items()).singleElement().satisfies(item -> {
            assertThat(item.derivationRequestKey()).isEqualTo(key);
            assertThat(item.testCaseVersionId()).isEqualTo(31L);
            assertThat(item.attemptNo()).isEqualTo(1);
        });
        verify(taskFeign, never()).createReplayBatch(any());
    }

    @Test
    void resumesBaselineWithOriginalExperimentDerivationKey() {
        EvaluationRunPersistenceService.RunDraft draft = pausedExperimentDraft();
        stubPausedExperiment(draft, experimentVariant("BASELINE"));
        String key = ExperimentService.derivationKey(91L, 88L, 31L, 1);
        when(taskFeign.createReplayBatch(any())).thenReturn(Result.ok(new ReplayBatchCreateVO(
                71L, 9L, List.of(new ReplayBatchItemVO(99L, key, 801L, "FAILED")),
                "a".repeat(64), "worker-token", Instant.now().plusSeconds(600))));
        when(persistenceService.attachDispatch(any(), any(), eq(2), anyMap()))
                .thenAnswer(invocation -> {
                    draft.run().setStatus(EvaluationRunStatus.RUNNING.name());
                    return draft;
                });

        service.resume(71L, new EvaluationRunResumeDTO(600L));

        ArgumentCaptor<ReplayBatchCreateDTO> request = ArgumentCaptor.forClass(ReplayBatchCreateDTO.class);
        verify(taskFeign).createReplayBatch(request.capture());
        assertThat(request.getValue().items()).singleElement().satisfies(item -> {
            assertThat(item.derivationRequestKey()).isEqualTo(key);
            assertThat(item.experimentVariantId()).isEqualTo(88L);
            assertThat(item.testCaseVersionId()).isEqualTo(31L);
            assertThat(item.attemptNo()).isEqualTo(1);
        });
        verify(taskFeign, never()).createExperimentBatch(any());
    }

    @Test
    void canceledPausedExperimentRenewsExistingTaskWithoutDerivingAgain() {
        EvaluationRunPersistenceService.RunDraft draft = pausedExperimentDraft();
        draft.run().setCancelRequested(true);
        EvaluationCaseAttemptEntity attempt = draft.cases().getFirst().attempt();
        when(runMapper.selectById(71L)).thenReturn(draft.run());
        when(caseRunMapper.selectList(any())).thenReturn(List.of(draft.cases().getFirst().caseRun()));
        when(attemptMapper.selectBatchIds(any())).thenReturn(List.of(attempt));
        when(testCaseVersionMapper.selectBatchIds(any())).thenReturn(List.of(publishedCase()));
        when(segmentService.nextBatchNo(71L)).thenReturn(2);
        when(attemptMapper.selectList(any())).thenReturn(List.of(attempt));
        when(feedbackMapper.selectList(any())).thenReturn(List.of());
        Instant expiresAt = Instant.now().plusSeconds(600);
        String taskIdsHash = StableSnapshotUtils.snapshotHash(1, List.of(801L));
        when(taskFeign.renewEvaluationWorkerCapability(any())).thenReturn(Result.ok(
                new EvaluationWorkerCapabilityVO(71L, 9L, taskIdsHash, "renewed-token", expiresAt)));
        EvaluationWorkerCapabilitySegmentEntity segment = new EvaluationWorkerCapabilitySegmentEntity();
        segment.setId(82L);
        when(segmentService.append(71L, 9L, 2, taskIdsHash, "renewed-token", expiresAt))
                .thenReturn(segment);
        when(segmentService.requireActiveCapabilities(any(), eq(71L), eq(9L)))
                .thenReturn(Map.of(82L, "renewed-token"));
        when(taskFeign.cancelEvaluationTasks(eq("renewed-token"), any())).thenReturn(Result.ok(
                List.of(new EvaluationTaskCancelVO(801L, true, "FAILED", "ALREADY_TERMINAL"))));

        service.resume(71L, new EvaluationRunResumeDTO(600L));

        assertThat(attempt.getCapabilitySegmentId()).isEqualTo(82L);
        verify(attemptMapper).updateBatch(any());
        verify(taskFeign, never()).createExperimentBatch(any());
        verify(taskFeign, never()).createReplayBatch(any());
        verify(taskFeign).cancelEvaluationTasks(eq("renewed-token"), any());
    }

    private EvaluationRunPersistenceService.RunDraft pausedExperimentDraft() {
        EvaluationRunPersistenceService.RunDraft draft = draft();
        draft.run().setStatus(EvaluationRunStatus.PAUSED.name());
        draft.run().setExperimentVariantId(88L);
        draft.cases().getFirst().attempt().setExecutionTaskId(801L);
        return draft;
    }

    private ExperimentVariantEntity experimentVariant(String role) {
        ExperimentVariantEntity variant = new ExperimentVariantEntity();
        variant.setId(88L);
        variant.setExperimentId(91L);
        variant.setSpaceId(9L);
        variant.setEvaluationRunId(71L);
        variant.setRole(role);
        variant.setCandidateConfigId("CANDIDATE".equals(role) ? 89L : null);
        variant.setCandidateSnapshotSchemaVersion(3);
        variant.setCandidateSnapshotHash("candidate-hash");
        return variant;
    }

    private void stubPausedExperiment(EvaluationRunPersistenceService.RunDraft draft,
                                      ExperimentVariantEntity variant) {
        when(runMapper.selectById(71L)).thenReturn(draft.run());
        when(caseRunMapper.selectList(any())).thenReturn(List.of(draft.cases().getFirst().caseRun()));
        when(attemptMapper.selectBatchIds(any())).thenReturn(List.of(draft.cases().getFirst().attempt()));
        when(testCaseVersionMapper.selectBatchIds(any())).thenReturn(List.of(publishedCase()));
        when(variantMapper.selectById(88L)).thenReturn(variant);
        when(segmentService.nextBatchNo(71L)).thenReturn(2);
    }

    private EvaluationTestCaseVersionEntity publishedCase() {
        EvaluationTestCaseVersionEntity version = new EvaluationTestCaseVersionEntity();
        version.setId(31L);
        version.setTestCaseId(32L);
        version.setSpaceId(9L);
        version.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        version.setSourceTaskId(99L);
        return version;
    }

    private EvaluationTestCaseEntity activeTestCase() {
        EvaluationTestCaseEntity testCase = new EvaluationTestCaseEntity();
        testCase.setId(32L);
        testCase.setArchived(false);
        return testCase;
    }

    private EvaluationRunPersistenceService.RunDraft draft() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(71L);
        run.setSpaceId(9L);
        run.setSingleTestCaseVersionId(31L);
        run.setStatus(EvaluationRunStatus.DISPATCHING.name());
        run.setCancelRequested(false);
        run.setCaseCount(1);
        EvaluationCaseRunEntity caseRun = new EvaluationCaseRunEntity();
        caseRun.setId(72L);
        caseRun.setRunId(71L);
        caseRun.setSpaceId(9L);
        caseRun.setTestCaseVersionId(31L);
        caseRun.setCurrentAttemptId(73L);
        caseRun.setStatus(EvaluationAttemptStatus.CREATED.name());
        EvaluationCaseAttemptEntity attempt = new EvaluationCaseAttemptEntity();
        attempt.setId(73L);
        attempt.setCaseRunId(72L);
        attempt.setRunId(71L);
        attempt.setSpaceId(9L);
        attempt.setAttemptNo(1);
        attempt.setStatus(EvaluationAttemptStatus.CREATED.name());
        return new EvaluationRunPersistenceService.RunDraft(run,
                List.of(new EvaluationRunPersistenceService.RunCaseDraft(caseRun, attempt)));
    }
}
