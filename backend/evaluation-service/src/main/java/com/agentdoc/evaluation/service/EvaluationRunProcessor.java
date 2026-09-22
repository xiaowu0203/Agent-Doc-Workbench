package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.EvaluationTaskBatchQueryDTO;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangeEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationTaskStatusVO;
import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationPauseReason;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
import com.agentdoc.evaluation.config.EvaluationRuntimeProperties;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluationOutcome;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluatorEngine;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationTestCaseVersionMapper;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.TestCaseEvaluatorMapper;
import com.agentdoc.evaluation.metric.EvaluatorResultWriteCommand;
import com.agentdoc.evaluation.observability.EvaluationTelemetry;
import com.agentdoc.evaluation.observability.EvaluationRuntimeMetrics;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.TestCaseEvaluatorEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import io.opentelemetry.context.Scope;

/**
 * 单个 EvaluationRun 的幂等状态推进器；不创建新的 Replay。
 * 定时对账Job的核心处理器，按能力分片批量查询任务状态，驱动Attempt状态流转、执行评估器计算，最后聚合整Run状态；
 * 仅做状态推进与评估执行，不发起新Replay任务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationRunProcessor {
    /**
     * Replay任务活跃状态集合，处于这些状态时继续等待，不进入评估阶段
     */
    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            "PENDING", "DISPATCHED", "RUNNING", "WAITING_INPUT", "WAITING_AUTH", "CANCELING");
    /**
     * Replay失败时允许执行的评估器白名单，其余评估器直接SKIP
     */
    private static final Set<String> REPLAY_FAILURE_ALLOWED_EVALUATORS = Set.of(
            "task-terminal-status", "audit-ledger-integrity");

    private final EvaluationRunMapper runMapper;
    private final EvaluationCaseRunMapper caseRunMapper;
    private final EvaluationCaseAttemptMapper attemptMapper;
    private final EvaluationTestCaseVersionMapper testCaseVersionMapper;
    private final TestCaseEvaluatorMapper testCaseEvaluatorMapper;
    private final EvaluatorVersionMapper evaluatorVersionMapper;
    private final EvaluationResultMapper resultMapper;
    private final WorkerCapabilitySegmentService segmentService;
    private final TaskFeign taskFeign;
    private final ExecutionMetricWriteService executionMetricWriteService;
    private final EvaluationResultWriteService resultWriteService;
    private final DeterministicEvaluatorEngine evaluatorEngine;
    private final EvaluationTelemetry evaluationTelemetry;
    private final EvaluationRuntimeMetrics runtimeMetrics;
    private final Executor evaluationExecutor;
    private final EvaluationRuntimeProperties runtimeProperties;

    /**
     * 单Run状态推进入口，幂等执行
     * 过滤终态/已暂停Run；加载CaseRun与Attempt，按能力分片分组，逐个分片推进，最后聚合Run整体状态；
     * 异常捕获，出现业务异常或运行时异常时将Run置为PAUSED
     */
    public void process(Long runId) {
        EvaluationRunEntity run = runMapper.selectById(runId);
        if (run == null || terminalRun(run.getStatus()) || EvaluationRunStatus.PAUSED.name().equals(run.getStatus())) {
            return;
        }
        if (EvaluationRunStatus.DISPATCHING.name().equals(run.getStatus())) {
            LocalDateTime staleBefore = LocalDateTime.now()
                    .minusSeconds(Math.max(5, runtimeProperties.getDispatchStaleSeconds()));
            if (run.getUpdatedAt() == null || !run.getUpdatedAt().isAfter(staleBefore)) {
                pause(run, EvaluationPauseReason.DISPATCH_UNAVAILABLE);
            }
            return;
        }
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseRunEntity>()
                        .eq(EvaluationCaseRunEntity::getRunId, runId));
        Map<Long, EvaluationCaseRunEntity> caseByAttempt = caseRuns.stream()
                .collect(Collectors.toMap(EvaluationCaseRunEntity::getCurrentAttemptId, Function.identity()));
        List<EvaluationCaseAttemptEntity> allAttempts = attemptMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseAttemptEntity>()
                        .eq(EvaluationCaseAttemptEntity::getRunId, runId));
        Map<Long, List<EvaluationCaseAttemptEntity>> bySegment = allAttempts.stream()
                .filter(attempt -> attempt.getCapabilitySegmentId() != null && attempt.getReplayTaskId() != null)
                .collect(Collectors.groupingBy(EvaluationCaseAttemptEntity::getCapabilitySegmentId));
        try {
            for (List<EvaluationCaseAttemptEntity> attempts : bySegment.values()) {
                processSegment(run, attempts, caseByAttempt);
            }
            aggregate(run, attemptMapper.selectBatchIds(caseByAttempt.keySet()), caseRuns);
        } catch (BusinessException exception) {
            String message = exception.getMessage();
            if (exception.getCode() == ErrorCode.UNAUTHORIZED.getCode()
                    || message != null && message.contains("过期")) {
                pause(run, EvaluationPauseReason.AUTHORIZATION_EXPIRED);
            } else {
                recordReconciliationFailure(run, exception);
            }
        } catch (RuntimeException exception) {
            recordReconciliationFailure(run, exception);
        }
    }

    /**
     * 按Worker能力分片批量查询任务状态与证据，分片内逐个Attempt推进
     */
    private void processSegment(EvaluationRunEntity run, List<EvaluationCaseAttemptEntity> attempts,
                                Map<Long, EvaluationCaseRunEntity> caseByAttempt) {
        String capability = segmentService.requireActiveCapability(attempts.getFirst().getCapabilitySegmentId(),
                run.getId(), run.getSpaceId());
        List<Long> taskIds = attempts.stream().map(EvaluationCaseAttemptEntity::getReplayTaskId).sorted().toList();
        EvaluationTaskBatchQueryDTO query = new EvaluationTaskBatchQueryDTO(run.getId(), run.getSpaceId(), taskIds);
        Map<Long, EvaluationTaskStatusVO> statuses = requireData(
                taskFeign.queryEvaluationTaskStatuses(capability, query)).stream()
                .collect(Collectors.toMap(EvaluationTaskStatusVO::taskId, Function.identity()));
        boolean needsEvidence = attempts.stream().anyMatch(attempt -> {
            EvaluationTaskStatusVO status = statuses.get(attempt.getReplayTaskId());
            return status != null && status.terminal()
                    && EvaluationAttemptStatus.valueOf(attempt.getStatus()).active();
        });
        Map<Long, EvaluationEvidenceBundleVO> evidence = needsEvidence
                ? requireData(taskFeign.queryEvaluationEvidence(capability, query)).stream()
                .collect(Collectors.toMap(EvaluationEvidenceBundleVO::taskId, Function.identity()))
                : Map.of();
        List<EvaluationCaseAttemptEntity> completedAttempts = attempts.stream().filter(attempt -> {
            EvaluationTaskStatusVO status = statuses.get(attempt.getReplayTaskId());
            return status != null && "COMPLETED".equals(status.status())
                    && EvaluationAttemptStatus.valueOf(attempt.getStatus()).active();
        }).toList();
        Map<Long, List<EvaluationDocumentChangeEvidenceVO>> documentChanges = needsEvidence
                && needsDocumentChangeEvidence(completedAttempts, caseByAttempt)
                ? requireData(taskFeign.queryEvaluationDocumentChanges(capability, query)).stream()
                .collect(Collectors.groupingBy(EvaluationDocumentChangeEvidenceVO::taskId))
                : Map.of();
        for (EvaluationCaseAttemptEntity attempt : attempts) {
            EvaluationCaseRunEntity caseRun = caseByAttempt.get(attempt.getId());
            if (caseRun != null) {
                advanceAttempt(attempt, caseRun, statuses.get(attempt.getReplayTaskId()),
                        evidence.get(attempt.getReplayTaskId()),
                        documentChanges.getOrDefault(attempt.getReplayTaskId(), List.of()),
                        Boolean.TRUE.equals(run.getCancelRequested()));
            }
        }
    }

    /**
     * 推进单个Attempt状态机
     * 根据Replay任务状态分支流转：活跃任务等待；终态任务拉取证据，执行评估器，更新Attempt最终状态
     */
    private void advanceAttempt(EvaluationCaseAttemptEntity attempt, EvaluationCaseRunEntity caseRun,
                                EvaluationTaskStatusVO taskStatus, EvaluationEvidenceBundleVO evidence,
                                List<EvaluationDocumentChangeEvidenceVO> documentChanges,
                                boolean cancelRequested) {
        if (!EvaluationAttemptStatus.valueOf(attempt.getStatus()).active()) {
            return;
        }
        if (taskStatus == null) {
            failReplay(attempt, caseRun, "TASK_UNAVAILABLE");
            return;
        }
        if (ACTIVE_TASK_STATUSES.contains(taskStatus.status())) {
            updateStatus(attempt, caseRun, cancelRequested
                    ? EvaluationAttemptStatus.CANCEL_PENDING : EvaluationAttemptStatus.REPLAY_RUNNING);
            return;
        }
        if (EvaluationAttemptStatus.valueOf(attempt.getStatus()).active() && evidence == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 已终态但评估事实不可用");
        }
        if (cancelRequested) {
            executionMetricWriteService.append(attempt.getId(), evidence);
            updateStatus(attempt, caseRun, EvaluationAttemptStatus.CANCELED);
            finish(attempt);
            return;
        }
        if ("TERMINATED".equals(taskStatus.status())) {
            executionMetricWriteService.append(attempt.getId(), evidence);
            updateStatus(attempt, caseRun, EvaluationAttemptStatus.CANCELED);
            finish(attempt);
            return;
        }
        boolean replayFailed = "FAILED".equals(taskStatus.status());
        if (!replayFailed && !"COMPLETED".equals(taskStatus.status())) {
            failReplay(attempt, caseRun, "TASK_STATUS_UNSUPPORTED");
            return;
        }
        updateStatus(attempt, caseRun, EvaluationAttemptStatus.EVALUATING);
        executionMetricWriteService.append(attempt.getId(), evidence);
        boolean evaluatorError = evaluate(attempt, caseRun, evidence, documentChanges,
                replayFailed, false, Set.of());
        EvaluationAttemptStatus finalStatus = replayFailed ? EvaluationAttemptStatus.REPLAY_FAILED
                : evaluatorError ? EvaluationAttemptStatus.EVALUATOR_FAILED : EvaluationAttemptStatus.COMPLETED;
        updateStatus(attempt, caseRun, finalStatus);
        finish(attempt);
    }

    /**
     * 评估器重试入口：指定Attempt、证据、目标评估器版本，强制重新执行评估，之后重新聚合Run状态
     */
    public void retryEvaluation(Long attemptId, EvaluationEvidenceBundleVO evidence,
                                List<EvaluationDocumentChangeEvidenceVO> documentChanges,
                                Set<Long> evaluatorVersionIds) {
        EvaluationCaseAttemptEntity attempt = attemptMapper.selectById(attemptId);
        EvaluationCaseRunEntity caseRun = attempt == null ? null : caseRunMapper.selectById(attempt.getCaseRunId());
        EvaluationRunEntity run = attempt == null ? null : runMapper.selectById(attempt.getRunId());
        if (attempt == null || caseRun == null || run == null || evidence == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Evaluation retry 目标不存在");
        }
        updateStatus(attempt, caseRun, EvaluationAttemptStatus.EVALUATING);
        run.setStatus(EvaluationRunStatus.RUNNING.name());
        run.setFinishedAt(null);
        runMapper.updateById(run);
        runMapper.update(null, new UpdateWrapper<EvaluationRunEntity>()
                .eq("id", run.getId())
                .set("finished_at", null)
                .set("pause_reason", null)
                .set("reconciliation_failure_count", 0));
        boolean error = evaluate(attempt, caseRun, evidence,
                documentChanges == null ? List.of() : documentChanges, false, true,
                evaluatorVersionIds == null ? Set.of() : evaluatorVersionIds);
        updateStatus(attempt, caseRun,
                error ? EvaluationAttemptStatus.EVALUATOR_FAILED : EvaluationAttemptStatus.COMPLETED);
        finish(attempt);
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseRunEntity>()
                        .eq(EvaluationCaseRunEntity::getRunId, run.getId()));
        List<Long> currentAttemptIds = caseRuns.stream()
                .map(EvaluationCaseRunEntity::getCurrentAttemptId).toList();
        aggregate(run, attemptMapper.selectBatchIds(currentAttemptIds), caseRuns);
    }

    /**
     * 执行该用例绑定的全部评估器；支持强制重跑、指定评估器版本白名单；
     * Replay失败时，仅白名单评估器运行，其余直接SKIPPED；异步执行评估器并做超时控制，写入EvaluationResult
     */
    private boolean evaluate(EvaluationCaseAttemptEntity attempt, EvaluationCaseRunEntity caseRun,
                             EvaluationEvidenceBundleVO evidence,
                             List<EvaluationDocumentChangeEvidenceVO> documentChanges, boolean replayFailed,
                             boolean force, Set<Long> targetEvaluatorVersionIds) {
        EvaluationTestCaseVersionEntity testCase = testCaseVersionMapper.selectById(caseRun.getTestCaseVersionId());
        List<TestCaseEvaluatorEntity> bindings = testCaseEvaluatorMapper.selectList(
                new LambdaQueryWrapper<TestCaseEvaluatorEntity>()
                        .eq(TestCaseEvaluatorEntity::getTestCaseVersionId, caseRun.getTestCaseVersionId())
                        .orderByAsc(TestCaseEvaluatorEntity::getSortOrder, TestCaseEvaluatorEntity::getId));
        Map<Long, EvaluatorVersionEntity> evaluators = evaluatorVersionMapper.selectBatchIds(bindings.stream()
                        .map(TestCaseEvaluatorEntity::getEvaluatorVersionId).toList()).stream()
                .collect(Collectors.toMap(EvaluatorVersionEntity::getId, Function.identity()));
        boolean error = false;
        for (TestCaseEvaluatorEntity binding : bindings) {
            EvaluatorVersionEntity evaluator = evaluators.get(binding.getEvaluatorVersionId());
            if (!targetEvaluatorVersionIds.isEmpty()
                    && !targetEvaluatorVersionIds.contains(binding.getEvaluatorVersionId())) {
                continue;
            }
            if (evaluator == null || !EvaluationVersionStatus.PUBLISHED.name().equals(evaluator.getStatus())) {
                error = true;
                continue;
            }
            EvaluationResultEntity existing = latestResult(attempt.getId(), evaluator.getId());
            if (existing != null && !force) {
                error |= EvaluationResultStatus.ERROR.name().equals(existing.getStatus());
                continue;
            }
            int evaluationAttemptNo = existing == null ? 1 : existing.getEvaluationAttemptNo() + 1;
            LocalDateTime startedAt = LocalDateTime.now();
            EvaluationTelemetry.EvaluationSpan telemetry = evaluationTelemetry.startEvaluator(attempt.getRunId(),
                    caseRun.getId(), attempt.getId(), evaluator.getId(), evaluator.getEvaluatorKey());
            long startedNanos = System.nanoTime();
            EvaluationResultStatus metricStatus = null;
            try (Scope ignored = telemetry.makeCurrent()) {
                DeterministicEvaluationOutcome outcome;
                if (replayFailed && !REPLAY_FAILURE_ALLOWED_EVALUATORS.contains(evaluator.getEvaluatorKey())) {
                    outcome = new DeterministicEvaluationOutcome(
                            EvaluationResultStatus.SKIPPED, "REPLAY_FAILED", "{\"reason\":\"REPLAY_FAILED\"}",
                            List.of(), List.of());
                } else {
                    String expected = binding.getExpectedJson() == null || binding.getExpectedJson().isBlank()
                            ? testCase.getExpectedJson() : binding.getExpectedJson();
                    outcome = executeEvaluator(evaluator, expected, evidence, documentChanges);
                }
                try {
                    appendResult(attempt, caseRun, evaluator, evaluationAttemptNo, outcome, startedAt,
                            telemetry.traceId(), telemetry.spanId());
                } catch (RuntimeException exception) {
                    telemetry.fail(exception);
                    throw exception;
                }
                telemetry.complete(outcome.status());
                metricStatus = outcome.status();
                error |= outcome.status() == EvaluationResultStatus.ERROR;
            } finally {
                runtimeMetrics.recordEvaluatorDuration(startedNanos, metricStatus);
            }
        }
        return error;
    }

    /**
     * 在独立线程池中执行单个 Evaluator。Evaluator 自身异常和超时固化为业务 ERROR；
     * 调度拒绝、线程中断等基础设施异常向上抛出，由 Run 对账重试处理。
     */
    private DeterministicEvaluationOutcome executeEvaluator(EvaluatorVersionEntity evaluator, String expected,
                                                             EvaluationEvidenceBundleVO evidence,
                                                             List<EvaluationDocumentChangeEvidenceVO> documentChanges) {
        CompletableFuture<DeterministicEvaluationOutcome> future;
        try {
            future = CompletableFuture.supplyAsync(
                    () -> evaluatorEngine.evaluate(evaluator.getEvaluatorKey(), evaluator.getConfigJson(),
                            expected, evidence, documentChanges),
                    evaluationExecutor);
        } catch (RejectedExecutionException exception) {
            runtimeMetrics.recordRejected();
            throw exception;
        }
        try {
            return future.get(runtimeProperties.getEvaluatorTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            runtimeMetrics.recordTimeout();
            return evaluatorError("EVALUATOR_TIMEOUT");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Evaluator 执行线程被中断", exception);
        } catch (ExecutionException exception) {
            return evaluatorError("EVALUATOR_CONTRACT_ERROR");
        }
    }

    private static DeterministicEvaluationOutcome evaluatorError(String reason) {
        return new DeterministicEvaluationOutcome(EvaluationResultStatus.ERROR, reason,
                "{\"reason\":\"" + reason + "\"}", List.of(), List.of());
    }

    private boolean needsDocumentChangeEvidence(List<EvaluationCaseAttemptEntity> attempts,
                                                Map<Long, EvaluationCaseRunEntity> caseByAttempt) {
        List<Long> caseVersionIds = attempts.stream().map(attempt -> caseByAttempt.get(attempt.getId()))
                .filter(java.util.Objects::nonNull).map(EvaluationCaseRunEntity::getTestCaseVersionId)
                .distinct().toList();
        if (caseVersionIds.isEmpty()) {
            return false;
        }
        List<TestCaseEvaluatorEntity> bindings = testCaseEvaluatorMapper.selectList(
                new LambdaQueryWrapper<TestCaseEvaluatorEntity>()
                        .in(TestCaseEvaluatorEntity::getTestCaseVersionId, caseVersionIds));
        if (bindings.isEmpty()) {
            return false;
        }
        return evaluatorVersionMapper.selectBatchIds(bindings.stream()
                        .map(TestCaseEvaluatorEntity::getEvaluatorVersionId).distinct().toList()).stream()
                .anyMatch(value -> "document-change-validator".equals(value.getEvaluatorKey()));
    }

    public boolean requiresDocumentChangeEvidence(Long testCaseVersionId, Set<Long> targetEvaluatorVersionIds) {
        List<TestCaseEvaluatorEntity> bindings = testCaseEvaluatorMapper.selectList(
                new LambdaQueryWrapper<TestCaseEvaluatorEntity>()
                        .eq(TestCaseEvaluatorEntity::getTestCaseVersionId, testCaseVersionId));
        Set<Long> targets = targetEvaluatorVersionIds == null ? Set.of() : targetEvaluatorVersionIds;
        List<Long> evaluatorIds = bindings.stream().map(TestCaseEvaluatorEntity::getEvaluatorVersionId)
                .filter(id -> targets.isEmpty() || targets.contains(id)).distinct().toList();
        return !evaluatorIds.isEmpty() && evaluatorVersionMapper.selectBatchIds(evaluatorIds).stream()
                .anyMatch(value -> "document-change-validator".equals(value.getEvaluatorKey()));
    }

    /**
     * 调用ResultWriteService原子写入一轮评估结果
     */
    private void appendResult(EvaluationCaseAttemptEntity attempt, EvaluationCaseRunEntity caseRun,
                              EvaluatorVersionEntity evaluator, int evaluationAttemptNo,
                              DeterministicEvaluationOutcome outcome, LocalDateTime startedAt,
                              String traceId, String spanId) {
        resultWriteService.append(new EvaluatorResultWriteCommand(attempt.getSpaceId(), attempt.getRunId(),
                caseRun.getId(), attempt.getId(), caseRun.getTestCaseVersionId(), evaluator.getId(),
                evaluationAttemptNo, outcome.status(), outcome.summaryCode(), outcome.detailsJson(),
                traceId, spanId, startedAt, LocalDateTime.now(), outcome.metrics(), outcome.evidence()));
    }

    /**
     * 查询该Attempt下某个评估器版本最新一条EvaluationResult，按evaluationAttemptNo+ID取最大
     */
    private EvaluationResultEntity latestResult(Long attemptId, Long evaluatorVersionId) {
        List<EvaluationResultEntity> results = resultMapper.selectList(
                new LambdaQueryWrapper<EvaluationResultEntity>()
                        .eq(EvaluationResultEntity::getCaseAttemptId, attemptId)
                        .eq(EvaluationResultEntity::getEvaluatorVersionId, evaluatorVersionId));
        return results.stream().max(Comparator.comparing(EvaluationResultEntity::getEvaluationAttemptNo)
                .thenComparing(EvaluationResultEntity::getId)).orElse(null);
    }

    /**
     * 聚合整Run状态：根据所有Attempt状态，使用状态策略计算Run最终状态；
     * 更新Run、同步CaseRun状态与Attempt状态
     */
    private void aggregate(EvaluationRunEntity run, List<EvaluationCaseAttemptEntity> attempts,
                           List<EvaluationCaseRunEntity> caseRuns) {
        List<EvaluationAttemptStatus> statuses = attempts.stream()
                .map(attempt -> EvaluationAttemptStatus.valueOf(attempt.getStatus())).toList();
        EvaluationRunStatus status = EvaluationRunStatePolicy.aggregate(statuses, false,
                Boolean.TRUE.equals(run.getCancelRequested()));
        run.setStatus(status.name());
        run.setPauseReason(null);
        run.setReconciliationFailureCount(0);
        run.setUpdatedAt(LocalDateTime.now());
        if (status == EvaluationRunStatus.COMPLETED || status == EvaluationRunStatus.COMPLETED_WITH_ERRORS
                || status == EvaluationRunStatus.CANCELED) {
            run.setFinishedAt(LocalDateTime.now());
        }
        runMapper.updateById(run);
        runMapper.update(null, new UpdateWrapper<EvaluationRunEntity>()
                .eq("id", run.getId())
                .set("pause_reason", null));
        Map<Long, EvaluationCaseAttemptEntity> byId = attempts.stream()
                .collect(Collectors.toMap(EvaluationCaseAttemptEntity::getId, Function.identity()));
        for (EvaluationCaseRunEntity caseRun : caseRuns) {
            EvaluationCaseAttemptEntity attempt = byId.get(caseRun.getCurrentAttemptId());
            if (attempt != null && !attempt.getStatus().equals(caseRun.getStatus())) {
                caseRun.setStatus(attempt.getStatus());
                caseRunMapper.updateById(caseRun);
            }
        }
    }

    /**
     * Replay任务异常，标记失败阶段与错误码，更新Attempt状态为REPLAY_FAILED
     */
    private void failReplay(EvaluationCaseAttemptEntity attempt, EvaluationCaseRunEntity caseRun, String code) {
        attempt.setFailureStage("RECONCILIATION");
        attempt.setFailureCode(code);
        updateStatus(attempt, caseRun, EvaluationAttemptStatus.REPLAY_FAILED);
        finish(attempt);
    }

    /**
     * 同步更新Attempt与CaseRun状态及更新时间
     */
    private void updateStatus(EvaluationCaseAttemptEntity attempt, EvaluationCaseRunEntity caseRun,
                              EvaluationAttemptStatus status) {
        attempt.setStatus(status.name());
        attempt.setUpdatedAt(LocalDateTime.now());
        attemptMapper.updateById(attempt);
        caseRun.setStatus(status.name());
        caseRun.setUpdatedAt(LocalDateTime.now());
        caseRunMapper.updateById(caseRun);
    }

    /**
     * 标记Attempt完成时间
     */
    private void finish(EvaluationCaseAttemptEntity attempt) {
        attempt.setFinishedAt(LocalDateTime.now());
        attemptMapper.updateById(attempt);
    }

    /**
     * 将Run置为PAUSED，记录暂停原因
     */
    private void pause(EvaluationRunEntity run, EvaluationPauseReason reason) {
        run.setStatus(EvaluationRunStatus.PAUSED.name());
        run.setPauseReason(reason.name());
        run.setUpdatedAt(LocalDateTime.now());
        runMapper.updateById(run);
    }

    /**
     * 记录连续基础设施错误；达到部署门限后才暂停，短暂故障留给下一轮对账自动恢复。
     */
    private void recordReconciliationFailure(EvaluationRunEntity run, RuntimeException exception) {
        runtimeMetrics.recordReconciliationFailure();
        int failures = (run.getReconciliationFailureCount() == null ? 0 : run.getReconciliationFailureCount()) + 1;
        run.setReconciliationFailureCount(failures);
        int threshold = Math.max(1, runtimeProperties.getMaxConsecutiveReconciliationFailures());
        if (failures >= threshold) {
            log.error("EvaluationRun 连续对账失败达到暂停门限 runId={}, failures={}, threshold={}, errorType={}",
                    run.getId(), failures, threshold, exception.getClass().getSimpleName());
            pause(run, EvaluationPauseReason.RECONCILIATION_BLOCKED);
            return;
        }
        log.warn("EvaluationRun 对账失败，等待下轮重试 runId={}, failures={}, threshold={}, errorType={}",
                run.getId(), failures, threshold, exception.getClass().getSimpleName());
        run.setUpdatedAt(LocalDateTime.now());
        runMapper.updateById(run);
    }

    /**
     * 判断Run是否属于终态，不再继续推进
     */
    private static boolean terminalRun(String status) {
        return EvaluationRunStatus.COMPLETED.name().equals(status)
                || EvaluationRunStatus.COMPLETED_WITH_ERRORS.name().equals(status)
                || EvaluationRunStatus.CANCELED.name().equals(status)
                || EvaluationRunStatus.FAILED.name().equals(status);
    }

    /**
     * Feign返回结果统一解包，非成功code抛业务异常
     */
    private static <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "task-service 调用失败" : result.message());
        }
        return result.data();
    }
}
