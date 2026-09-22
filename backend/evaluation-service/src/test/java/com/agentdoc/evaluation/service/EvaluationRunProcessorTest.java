package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangeEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.feign.vo.EvaluationTaskStatusVO;
import com.agentdoc.evaluation.config.EvaluationRuntimeProperties;
import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationPauseReason;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluationOutcome;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluatorEngine;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.TestCaseEvaluatorMapper;
import com.agentdoc.evaluation.observability.EvaluationTelemetry;
import com.agentdoc.evaluation.observability.EvaluationRuntimeMetrics;
import com.agentdoc.evaluation.metric.EvaluatorResultWriteCommand;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.TestCaseEvaluatorEntity;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationRunProcessorTest {

    @Mock private EvaluationRunMapper runMapper;
    @Mock private EvaluationCaseRunMapper caseRunMapper;
    @Mock private EvaluationCaseAttemptMapper attemptMapper;
    @Mock private EvaluationTestCaseVersionMapper testCaseVersionMapper;
    @Mock private TestCaseEvaluatorMapper testCaseEvaluatorMapper;
    @Mock private EvaluatorVersionMapper evaluatorVersionMapper;
    @Mock private EvaluationResultMapper resultMapper;
    @Mock private WorkerCapabilitySegmentService segmentService;
    @Mock private TaskFeign taskFeign;
    @Mock private ExecutionMetricWriteService executionMetricWriteService;
    @Mock private EvaluationResultWriteService resultWriteService;
    @Mock private DeterministicEvaluatorEngine evaluatorEngine;
    @Mock private EvaluationTelemetry evaluationTelemetry;
    @Mock private EvaluationRuntimeMetrics runtimeMetrics;

    private EvaluationRunProcessor processor;

    @BeforeEach
    void setUp() {
        EvaluationRuntimeProperties properties = new EvaluationRuntimeProperties();
        properties.setEvaluatorTimeoutMillis(1000);
        Executor directExecutor = Runnable::run;
        processor = new EvaluationRunProcessor(runMapper, caseRunMapper, attemptMapper, testCaseVersionMapper,
                testCaseEvaluatorMapper, evaluatorVersionMapper, resultMapper, segmentService, taskFeign,
                executionMetricWriteService, resultWriteService, evaluatorEngine, evaluationTelemetry,
                runtimeMetrics, directExecutor, properties);
    }

    @Test
    void reloadsAttemptAfterAdvanceBeforeAggregatingRun() {
        EvaluationRunEntity run = run();
        EvaluationCaseRunEntity caseRun = caseRun();
        EvaluationCaseAttemptEntity attempt = attempt(EvaluationAttemptStatus.REPLAY_CREATED);
        stubRun(run, caseRun, attempt);
        when(segmentService.requireActiveCapability(81L, 71L, 9L)).thenReturn("capability");
        when(taskFeign.queryEvaluationTaskStatuses(eq("capability"), any())).thenReturn(Result.ok(List.of(
                new EvaluationTaskStatusVO(801L, 2, "RUNNING", false, 901L, "trace", null, null))));

        processor.process(71L);

        assertThat(attempt.getStatus()).isEqualTo(EvaluationAttemptStatus.REPLAY_RUNNING.name());
        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.RUNNING.name());
        assertThat(run.getFinishedAt()).isNull();
        verify(taskFeign, never()).queryEvaluationEvidence(any(), any());
    }

    @Test
    void staleDispatchingRunBecomesRecoverablePauseAfterRestart() {
        EvaluationRunEntity run = run();
        run.setStatus(EvaluationRunStatus.DISPATCHING.name());
        run.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        when(runMapper.selectById(71L)).thenReturn(run);

        processor.process(71L);

        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.PAUSED.name());
        assertThat(run.getPauseReason()).isEqualTo(EvaluationPauseReason.DISPATCH_UNAVAILABLE.name());
        verify(caseRunMapper, never()).selectList(any());
        verify(runMapper).updateById(run);
    }

    @Test
    void freshDispatchingRunIsNotInterruptedByReconciliation() {
        EvaluationRunEntity run = run();
        run.setStatus(EvaluationRunStatus.DISPATCHING.name());
        run.setUpdatedAt(LocalDateTime.now());
        when(runMapper.selectById(71L)).thenReturn(run);

        processor.process(71L);

        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.DISPATCHING.name());
        verify(runMapper, never()).updateById(any(EvaluationRunEntity.class));
    }

    @Test
    void completesTerminalReplayAndPersistsExecutionMetrics() {
        EvaluationRunEntity run = run();
        EvaluationCaseRunEntity caseRun = caseRun();
        EvaluationCaseAttemptEntity attempt = attempt(EvaluationAttemptStatus.REPLAY_RUNNING);
        stubRun(run, caseRun, attempt);
        when(segmentService.requireActiveCapability(81L, 71L, 9L)).thenReturn("capability");
        when(taskFeign.queryEvaluationTaskStatuses(eq("capability"), any())).thenReturn(Result.ok(List.of(
                new EvaluationTaskStatusVO(801L, 3, "COMPLETED", true, 901L, "trace", null, null))));
        EvaluationEvidenceBundleVO evidence = evidence();
        when(taskFeign.queryEvaluationEvidence(eq("capability"), any()))
                .thenReturn(Result.ok(List.of(evidence)));
        when(testCaseEvaluatorMapper.selectList(any())).thenReturn(List.of());

        processor.process(71L);

        assertThat(attempt.getStatus()).isEqualTo(EvaluationAttemptStatus.COMPLETED.name());
        assertThat(attempt.getFinishedAt()).isNotNull();
        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.COMPLETED.name());
        assertThat(run.getFinishedAt()).isNotNull();
        verify(executionMetricWriteService).append(73L, evidence);
        verify(taskFeign, never()).queryEvaluationDocumentChanges(any(), any());
    }

    @Test
    void loadsDocumentChangeEvidenceOnlyForBoundDocumentValidator() {
        EvaluationRunEntity run = run();
        EvaluationCaseRunEntity caseRun = caseRun();
        EvaluationCaseAttemptEntity attempt = attempt(EvaluationAttemptStatus.REPLAY_RUNNING);
        stubRun(run, caseRun, attempt);
        when(segmentService.requireActiveCapability(81L, 71L, 9L)).thenReturn("capability");
        when(taskFeign.queryEvaluationTaskStatuses(eq("capability"), any())).thenReturn(Result.ok(List.of(
                new EvaluationTaskStatusVO(801L, 3, "COMPLETED", true, 901L, "trace", null, null))));
        EvaluationEvidenceBundleVO evidence = evidence();
        when(taskFeign.queryEvaluationEvidence(eq("capability"), any()))
                .thenReturn(Result.ok(List.of(evidence)));
        EvaluationDocumentChangeEvidenceVO documentChange = new EvaluationDocumentChangeEvidenceVO(
                801L, 902L, "a".repeat(64), true, false, "b".repeat(64), null);
        when(taskFeign.queryEvaluationDocumentChanges(eq("capability"), any()))
                .thenReturn(Result.ok(List.of(documentChange)));

        EvaluationTestCaseVersionEntity testCase = new EvaluationTestCaseVersionEntity();
        testCase.setExpectedJson("{}");
        when(testCaseVersionMapper.selectById(31L)).thenReturn(testCase);
        TestCaseEvaluatorEntity binding = new TestCaseEvaluatorEntity();
        binding.setEvaluatorVersionId(41L);
        when(testCaseEvaluatorMapper.selectList(any())).thenReturn(List.of(binding));
        EvaluatorVersionEntity evaluator = new EvaluatorVersionEntity();
        evaluator.setId(41L);
        evaluator.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        evaluator.setEvaluatorKey("document-change-validator");
        evaluator.setConfigJson("{}");
        when(evaluatorVersionMapper.selectBatchIds(any())).thenReturn(List.of(evaluator));
        when(resultMapper.selectList(any())).thenReturn(List.of());
        when(evaluatorEngine.evaluate("document-change-validator", "{}", "{}", evidence,
                List.of(documentChange))).thenReturn(new DeterministicEvaluationOutcome(
                EvaluationResultStatus.PASSED, "DOCUMENT_CHANGE_VALID", "{}", List.of(), List.of()));
        EvaluationTelemetry.EvaluationSpan telemetrySpan = org.mockito.Mockito.mock(
                EvaluationTelemetry.EvaluationSpan.class);
        when(evaluationTelemetry.startEvaluator(71L, 72L, 73L, 41L, "document-change-validator"))
                .thenReturn(telemetrySpan);
        when(telemetrySpan.makeCurrent()).thenReturn(org.mockito.Mockito.mock(Scope.class));

        processor.process(71L);

        verify(taskFeign).queryEvaluationDocumentChanges(eq("capability"), any());
        verify(evaluatorEngine).evaluate("document-change-validator", "{}", "{}", evidence,
                List.of(documentChange));
        assertThat(attempt.getStatus()).isEqualTo(EvaluationAttemptStatus.COMPLETED.name());
    }

    @Test
    void writesEvaluatorResultWithEvaluationSpanIdentity() {
        EvaluationRunEntity run = run();
        EvaluationCaseRunEntity caseRun = caseRun();
        EvaluationCaseAttemptEntity attempt = attempt(EvaluationAttemptStatus.REPLAY_RUNNING);
        stubRun(run, caseRun, attempt);
        when(segmentService.requireActiveCapability(81L, 71L, 9L)).thenReturn("capability");
        when(taskFeign.queryEvaluationTaskStatuses(eq("capability"), any())).thenReturn(Result.ok(List.of(
                new EvaluationTaskStatusVO(801L, 3, "COMPLETED", true, 901L, "trace", null, null))));
        EvaluationEvidenceBundleVO evidence = evidence();
        when(taskFeign.queryEvaluationEvidence(eq("capability"), any()))
                .thenReturn(Result.ok(List.of(evidence)));
        EvaluationTestCaseVersionEntity testCase = new EvaluationTestCaseVersionEntity();
        testCase.setExpectedJson("{}");
        when(testCaseVersionMapper.selectById(31L)).thenReturn(testCase);
        TestCaseEvaluatorEntity binding = new TestCaseEvaluatorEntity();
        binding.setEvaluatorVersionId(41L);
        binding.setExpectedJson("{}");
        when(testCaseEvaluatorMapper.selectList(any())).thenReturn(List.of(binding));
        EvaluatorVersionEntity evaluator = new EvaluatorVersionEntity();
        evaluator.setId(41L);
        evaluator.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        evaluator.setEvaluatorKey("artifact-contract");
        evaluator.setConfigJson("{}");
        when(evaluatorVersionMapper.selectBatchIds(any())).thenReturn(List.of(evaluator));
        when(resultMapper.selectList(any())).thenReturn(List.of());
        DeterministicEvaluationOutcome outcome = new DeterministicEvaluationOutcome(
                EvaluationResultStatus.PASSED, "ARTIFACT_CONTRACT_PASSED", "{}", List.of(), List.of());
        when(evaluatorEngine.evaluate("artifact-contract", "{}", "{}", evidence, List.of())).thenReturn(outcome);
        EvaluationTelemetry.EvaluationSpan telemetrySpan = org.mockito.Mockito.mock(
                EvaluationTelemetry.EvaluationSpan.class);
        Scope scope = org.mockito.Mockito.mock(Scope.class);
        when(evaluationTelemetry.startEvaluator(71L, 72L, 73L, 41L, "artifact-contract"))
                .thenReturn(telemetrySpan);
        when(telemetrySpan.makeCurrent()).thenReturn(scope);
        when(telemetrySpan.traceId()).thenReturn("a".repeat(32));
        when(telemetrySpan.spanId()).thenReturn("b".repeat(16));

        processor.process(71L);

        ArgumentCaptor<EvaluatorResultWriteCommand> command =
                ArgumentCaptor.forClass(EvaluatorResultWriteCommand.class);
        verify(resultWriteService).append(command.capture());
        assertThat(command.getValue().traceId()).isEqualTo("a".repeat(32));
        assertThat(command.getValue().spanId()).isEqualTo("b".repeat(16));
        verify(telemetrySpan).complete(EvaluationResultStatus.PASSED);
        verify(scope).close();
    }

    @Test
    void resultPersistenceFailureIsRetriedAsReconciliationFailureWithoutDuplicateErrorResult() {
        EvaluationRunEntity run = run();
        EvaluationCaseRunEntity caseRun = caseRun();
        EvaluationCaseAttemptEntity attempt = attempt(EvaluationAttemptStatus.REPLAY_RUNNING);
        stubRun(run, caseRun, attempt);
        when(segmentService.requireActiveCapability(81L, 71L, 9L)).thenReturn("capability");
        when(taskFeign.queryEvaluationTaskStatuses(eq("capability"), any())).thenReturn(Result.ok(List.of(
                new EvaluationTaskStatusVO(801L, 3, "COMPLETED", true, 901L, "trace", null, null))));
        EvaluationEvidenceBundleVO evidence = evidence();
        when(taskFeign.queryEvaluationEvidence(eq("capability"), any()))
                .thenReturn(Result.ok(List.of(evidence)));
        EvaluationTestCaseVersionEntity testCase = new EvaluationTestCaseVersionEntity();
        testCase.setExpectedJson("{}");
        when(testCaseVersionMapper.selectById(31L)).thenReturn(testCase);
        TestCaseEvaluatorEntity binding = new TestCaseEvaluatorEntity();
        binding.setEvaluatorVersionId(41L);
        when(testCaseEvaluatorMapper.selectList(any())).thenReturn(List.of(binding));
        EvaluatorVersionEntity evaluator = new EvaluatorVersionEntity();
        evaluator.setId(41L);
        evaluator.setStatus(EvaluationVersionStatus.PUBLISHED.name());
        evaluator.setEvaluatorKey("artifact-contract");
        evaluator.setConfigJson("{}");
        when(evaluatorVersionMapper.selectBatchIds(any())).thenReturn(List.of(evaluator));
        when(resultMapper.selectList(any())).thenReturn(List.of());
        when(evaluatorEngine.evaluate(eq("artifact-contract"), eq("{}"), eq("{}"), eq(evidence), anyList())).thenReturn(
                new DeterministicEvaluationOutcome(EvaluationResultStatus.PASSED,
                        "ARTIFACT_CONTRACT_PASSED", "{}", List.of(), List.of()));
        EvaluationTelemetry.EvaluationSpan telemetrySpan = org.mockito.Mockito.mock(
                EvaluationTelemetry.EvaluationSpan.class);
        Scope scope = org.mockito.Mockito.mock(Scope.class);
        when(evaluationTelemetry.startEvaluator(71L, 72L, 73L, 41L, "artifact-contract"))
                .thenReturn(telemetrySpan);
        when(telemetrySpan.makeCurrent()).thenReturn(scope);
        doThrow(new IllegalStateException("database unavailable"))
                .when(resultWriteService).append(any());

        processor.process(71L);

        assertThat(attempt.getStatus()).isEqualTo(EvaluationAttemptStatus.EVALUATING.name());
        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.RUNNING.name());
        assertThat(run.getReconciliationFailureCount()).isEqualTo(1);
        verify(resultWriteService).append(any());
        verify(telemetrySpan).fail(any(IllegalStateException.class));
        verify(telemetrySpan, never()).complete(any());
        verify(scope).close();
    }

    @Test
    void pausesRunWhenWorkerCapabilityExpired() {
        EvaluationRunEntity run = run();
        EvaluationCaseRunEntity caseRun = caseRun();
        EvaluationCaseAttemptEntity attempt = attempt(EvaluationAttemptStatus.REPLAY_CREATED);
        stubRun(run, caseRun, attempt);
        when(segmentService.requireActiveCapability(81L, 71L, 9L))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "WorkerCapability 已过期"));

        processor.process(71L);

        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.PAUSED.name());
        assertThat(run.getPauseReason()).isEqualTo(EvaluationPauseReason.AUTHORIZATION_EXPIRED.name());
        verify(taskFeign, never()).queryEvaluationTaskStatuses(any(), any());
    }

    @Test
    void missingTaskProjectionEndsAttemptWithInfrastructureError() {
        EvaluationRunEntity run = run();
        EvaluationCaseRunEntity caseRun = caseRun();
        EvaluationCaseAttemptEntity attempt = attempt(EvaluationAttemptStatus.REPLAY_RUNNING);
        stubRun(run, caseRun, attempt);
        when(segmentService.requireActiveCapability(81L, 71L, 9L)).thenReturn("capability");
        when(taskFeign.queryEvaluationTaskStatuses(eq("capability"), any())).thenReturn(Result.ok(List.of()));

        processor.process(71L);

        assertThat(attempt.getStatus()).isEqualTo(EvaluationAttemptStatus.REPLAY_FAILED.name());
        assertThat(attempt.getFailureCode()).isEqualTo("TASK_UNAVAILABLE");
        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.COMPLETED_WITH_ERRORS.name());
    }

    @Test
    void pausesOnlyAfterConsecutiveReconciliationFailuresReachThreshold() {
        EvaluationRunEntity run = run();
        EvaluationCaseRunEntity caseRun = caseRun();
        EvaluationCaseAttemptEntity attempt = attempt(EvaluationAttemptStatus.REPLAY_RUNNING);
        stubRun(run, caseRun, attempt);
        when(segmentService.requireActiveCapability(81L, 71L, 9L))
                .thenThrow(new IllegalStateException("temporary dependency failure"));

        processor.process(71L);
        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.RUNNING.name());
        assertThat(run.getReconciliationFailureCount()).isEqualTo(1);

        processor.process(71L);
        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.RUNNING.name());
        assertThat(run.getReconciliationFailureCount()).isEqualTo(2);

        processor.process(71L);
        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.PAUSED.name());
        assertThat(run.getPauseReason()).isEqualTo(EvaluationPauseReason.RECONCILIATION_BLOCKED.name());
        assertThat(run.getReconciliationFailureCount()).isEqualTo(3);
    }

    @Test
    void retryEvaluationAggregatesExistingAttemptsWithoutReconcilingReplayTasks() {
        EvaluationRunEntity run = run();
        run.setStatus(EvaluationRunStatus.PAUSED.name());
        run.setPauseReason(EvaluationPauseReason.RECONCILIATION_BLOCKED.name());
        run.setReconciliationFailureCount(3);
        run.setFinishedAt(LocalDateTime.now().minusMinutes(1));
        EvaluationCaseRunEntity caseRun = caseRun();
        EvaluationCaseAttemptEntity attempt = attempt(EvaluationAttemptStatus.COMPLETED);
        when(attemptMapper.selectById(73L)).thenReturn(attempt);
        when(caseRunMapper.selectById(72L)).thenReturn(caseRun);
        when(runMapper.selectById(71L)).thenReturn(run);
        when(testCaseEvaluatorMapper.selectList(any())).thenReturn(List.of());
        when(caseRunMapper.selectList(any())).thenReturn(List.of(caseRun));
        when(attemptMapper.selectBatchIds(List.of(73L))).thenReturn(List.of(attempt));

        processor.retryEvaluation(73L, evidence(), List.of(), Set.of());

        assertThat(attempt.getStatus()).isEqualTo(EvaluationAttemptStatus.COMPLETED.name());
        assertThat(run.getStatus()).isEqualTo(EvaluationRunStatus.COMPLETED.name());
        assertThat(run.getPauseReason()).isNull();
        assertThat(run.getReconciliationFailureCount()).isZero();
        assertThat(run.getFinishedAt()).isNotNull();
        verify(taskFeign, never()).queryEvaluationTaskStatuses(any(), any());
        verify(taskFeign, never()).queryEvaluationEvidence(any(), any());
    }

    private void stubRun(EvaluationRunEntity run, EvaluationCaseRunEntity caseRun,
                         EvaluationCaseAttemptEntity attempt) {
        when(runMapper.selectById(71L)).thenReturn(run);
        when(caseRunMapper.selectList(any())).thenReturn(List.of(caseRun));
        when(attemptMapper.selectList(any())).thenReturn(List.of(attempt));
        lenient().when(attemptMapper.selectBatchIds(any())).thenAnswer(invocation -> List.of(attempt));
    }

    private static EvaluationRunEntity run() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(71L);
        run.setSpaceId(9L);
        run.setStatus(EvaluationRunStatus.RUNNING.name());
        run.setCancelRequested(false);
        run.setCaseCount(1);
        run.setReconciliationFailureCount(0);
        return run;
    }

    private static EvaluationCaseRunEntity caseRun() {
        EvaluationCaseRunEntity caseRun = new EvaluationCaseRunEntity();
        caseRun.setId(72L);
        caseRun.setRunId(71L);
        caseRun.setSpaceId(9L);
        caseRun.setTestCaseVersionId(31L);
        caseRun.setCurrentAttemptId(73L);
        caseRun.setStatus(EvaluationAttemptStatus.REPLAY_CREATED.name());
        return caseRun;
    }

    private static EvaluationCaseAttemptEntity attempt(EvaluationAttemptStatus status) {
        EvaluationCaseAttemptEntity attempt = new EvaluationCaseAttemptEntity();
        attempt.setId(73L);
        attempt.setCaseRunId(72L);
        attempt.setRunId(71L);
        attempt.setSpaceId(9L);
        attempt.setAttemptNo(1);
        attempt.setReplayTaskId(801L);
        attempt.setCapabilitySegmentId(81L);
        attempt.setStatus(status.name());
        return attempt;
    }

    private static EvaluationEvidenceBundleVO evidence() {
        return new EvaluationEvidenceBundleVO(801L, 9L, 3, "COMPLETED", "done", 901L,
                "COMPLETED", "trace", "span", null, null, 10L, 0L, 4L,
                new BigDecimal("0.01"), "USD", 0, 1, 0, 0, 0, List.of());
    }
}
