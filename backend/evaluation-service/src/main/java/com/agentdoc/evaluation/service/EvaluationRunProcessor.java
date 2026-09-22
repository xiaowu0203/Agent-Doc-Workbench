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
 * 单个 EvaluationRun 的幂等状态推进器；**不创建新的 Replay 任务**。
 * <p>定时对账 Job 的核心处理器，按 Worker 能力分片批量拉取回放任务状态，
 * 驱动 CaseAttempt / CaseRun 状态机流转、调用评估器引擎执行判定、原子写入评估结果，
 * 最后按聚合策略算出整 Run 的最终状态并落库。
 * 职责边界：只做对账、状态推进、评估执行；分发、新建重试 Replay 由 {@link EvaluationRunPersistenceService} 负责。
 * 异常策略：短时对账异常累加失败计数，超过阈值才将 Run 置为 PAUSED，避免瞬时抖动误暂停。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationRunProcessor {

    /**
     * Replay 任务活跃状态集合：处于以下状态说明回放还在进行，评估器阶段暂不触发，继续等待下一轮对账
     */
    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            "PENDING", "DISPATCHED", "RUNNING", "WAITING_INPUT", "WAITING_AUTH", "CANCELING");

    /**
     * Replay 回放失败时允许执行的评估器白名单；不在白名单内的评估器直接标记为 SKIPPED，不运行
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
     * 单 Run 状态推进入口，幂等执行，可被定时Job重复调用
     * <p>流程：
     * 1. 过滤已终态 / 已暂停的 Run，直接返回；
     * 2. DISPATCHING 状态下检测超时，超时未分发则标记为 PAUSED；
     * 3. 加载本 Run 全部 CaseRun、Attempt；按 capabilitySegmentId 分片分组；
     * 4. 逐个分片批量查询回放任务状态、证据、文档变更证据，分片内循环推进每个 Attempt；
     * 5. 所有分片处理完成后，聚合全部 Attempt 状态，更新 Run 整体状态；
     * 6. 捕获业务异常 / 运行时异常，按规则暂停 Run 或累加对账失败计数，留给下一轮重试。
     * </p>
     * @param runId 待推进的评估任务ID
     */
    public void process(Long runId) {
        EvaluationRunEntity run = runMapper.selectById(runId);
        // 已删除、终态、已暂停：不再推进
        if (run == null || terminalRun(run.getStatus()) || EvaluationRunStatus.PAUSED.name().equals(run.getStatus())) {
            return;
        }

        // 分发超时检测：长时间停留在DISPATCHING且未更新，判定资源不可用，暂停
        if (EvaluationRunStatus.DISPATCHING.name().equals(run.getStatus())) {
            LocalDateTime staleBefore = LocalDateTime.now()
                    .minusSeconds(Math.max(5, runtimeProperties.getDispatchStaleSeconds()));
            if (run.getUpdatedAt() == null || !run.getUpdatedAt().isAfter(staleBefore)) {
                pause(run, EvaluationPauseReason.DISPATCH_UNAVAILABLE);
            }
            return;
        }

        // 加载本Run所有CaseRun，建立 currentAttemptId -> CaseRun 映射
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseRunEntity>()
                        .eq(EvaluationCaseRunEntity::getRunId, runId));
        Map<Long, EvaluationCaseRunEntity> caseByAttempt = caseRuns.stream()
                .collect(Collectors.toMap(EvaluationCaseRunEntity::getCurrentAttemptId, Function.identity()));

        // 加载所有Attempt，过滤已绑定分片与回放任务的记录，按能力分片分组
        List<EvaluationCaseAttemptEntity> allAttempts = attemptMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseAttemptEntity>()
                        .eq(EvaluationCaseAttemptEntity::getRunId, runId));
        Map<Long, List<EvaluationCaseAttemptEntity>> bySegment = allAttempts.stream()
                .filter(attempt -> attempt.getCapabilitySegmentId() != null && attempt.getReplayTaskId() != null)
                .collect(Collectors.groupingBy(EvaluationCaseAttemptEntity::getCapabilitySegmentId));

        try {
            // 按分片并行/串行推进（分片内批量拉取，减少feign调用）
            for (List<EvaluationCaseAttemptEntity> attempts : bySegment.values()) {
                processSegment(run, attempts, caseByAttempt);
            }
            // 所有分片处理完毕，聚合Run顶层状态
            aggregate(run, attemptMapper.selectBatchIds(caseByAttempt.keySet()), caseRuns);
        } catch (BusinessException exception) {
            String message = exception.getMessage();
            // 鉴权过期类异常直接暂停，不做多次重试
            if (exception.getCode() == ErrorCode.UNAUTHORIZED.getCode()
                    || message != null && message.contains("过期")) {
                pause(run, EvaluationPauseReason.AUTHORIZATION_EXPIRED);
            } else {
                recordReconciliationFailure(run, exception);
            }
        } catch (RuntimeException exception) {
            // 其他运行时异常，累加对账失败计数
            recordReconciliationFailure(run, exception);
        }
    }

    /**
     * 按Worker能力分片批量查询回放任务状态与证据，分片内逐个Attempt推进状态
     * <p>优势：同一分片复用同一份 capability 鉴权，批量查询task状态减少RPC次数；
     * 按需拉取证据：只有存在活跃且已完成回放的Attempt才拉取证据包和文档变更证据。
     * </p>
     * @param run 评估任务主记录
     * @param attempts 当前分片下所有Attempt列表
     * @param caseByAttempt attemptId -> CaseRun 映射
     */
    private void processSegment(EvaluationRunEntity run, List<EvaluationCaseAttemptEntity> attempts,
                                Map<Long, EvaluationCaseRunEntity> caseByAttempt) {
        // 校验分片能力有效，拿到对应capability用于下游task-feign鉴权
        String capability = segmentService.requireActiveCapability(attempts.getFirst().getCapabilitySegmentId(),
                run.getId(), run.getSpaceId());

        // 批量查询回放任务状态
        List<Long> taskIds = attempts.stream().map(EvaluationCaseAttemptEntity::getReplayTaskId).sorted().toList();
        EvaluationTaskBatchQueryDTO query = new EvaluationTaskBatchQueryDTO(run.getId(), run.getSpaceId(), taskIds);
        Map<Long, EvaluationTaskStatusVO> statuses = requireData(
                taskFeign.queryEvaluationTaskStatuses(capability, query)).stream()
                .collect(Collectors.toMap(EvaluationTaskStatusVO::taskId, Function.identity()));

        // 判断是否需要拉取证据包：存在活跃Attempt且对应回放任务已经终态
        boolean needsEvidence = attempts.stream().anyMatch(attempt -> {
            EvaluationTaskStatusVO status = statuses.get(attempt.getReplayTaskId());
            return status != null && status.terminal()
                    && EvaluationAttemptStatus.valueOf(attempt.getStatus()).active();
        });

        Map<Long, EvaluationEvidenceBundleVO> evidence = needsEvidence
                ? requireData(taskFeign.queryEvaluationEvidence(capability, query)).stream()
                .collect(Collectors.toMap(EvaluationEvidenceBundleVO::taskId, Function.identity()))
                : Map.of();

        // 筛选回放已COMPLETED的Attempt，判断是否存在需要document-change证据的评估器，按需拉取
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

        // 分片内逐个推进Attempt状态机
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
     * 推进单个 Attempt 状态机
     * <p>分支逻辑：
     * 1. Attempt已是终态 → 跳过；
     * 2. taskStatus不存在 → 标记回放不可用失败；
     * 3. 回放任务仍在活跃中 → 更新为REPLAY_RUNNING / CANCEL_PENDING；
     * 4. 回放已终态但证据缺失 → 抛冲突异常，触发对账重试；
     * 5. Run收到取消请求 / 任务被TERMINATED → 记录指标，标记Attempt为CANCELED；
     * 6. 回放FAILED / COMPLETED → 进入EVALUATING阶段，执行评估器，根据评估结果落终态；
     * </p>
     * @param attempt 用例尝试记录
     * @param caseRun 所属用例运行记录
     * @param taskStatus 回放任务状态VO
     * @param evidence 回放证据包
     * @param documentChanges 文档变更证据列表
     * @param cancelRequested 当前Run是否收到取消请求
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

        // 回放任务还在跑，等待下一轮对账
        if (ACTIVE_TASK_STATUSES.contains(taskStatus.status())) {
            updateStatus(attempt, caseRun, cancelRequested
                    ? EvaluationAttemptStatus.CANCEL_PENDING : EvaluationAttemptStatus.REPLAY_RUNNING);
            return;
        }

        // 回放已经终态但证据缺失，属于数据不一致，抛异常触发对账重试
        if (EvaluationAttemptStatus.valueOf(attempt.getStatus()).active() && evidence == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Replay 已终态但评估事实不可用");
        }

        // Run全局取消请求，直接标记取消
        if (cancelRequested) {
            executionMetricWriteService.append(attempt.getId(), evidence);
            updateStatus(attempt, caseRun, EvaluationAttemptStatus.CANCELED);
            finish(attempt);
            return;
        }

        // 任务被外部终止
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

        // 进入评估阶段
        updateStatus(attempt, caseRun, EvaluationAttemptStatus.EVALUATING);
        executionMetricWriteService.append(attempt.getId(), evidence);

        // 执行所有绑定评估器
        boolean evaluatorError = evaluate(attempt, caseRun, evidence, documentChanges,
                replayFailed, false, Set.of());

        // 判定Attempt最终状态
        EvaluationAttemptStatus finalStatus = replayFailed ? EvaluationAttemptStatus.REPLAY_FAILED
                : evaluatorError ? EvaluationAttemptStatus.EVALUATOR_FAILED : EvaluationAttemptStatus.COMPLETED;
        updateStatus(attempt, caseRun, finalStatus);
        finish(attempt);
    }

    /**
     * 评估器重跑入口：针对指定Attempt强制重新执行部分/全部评估器，不依赖定时对账Job。
     * <p>常用于人工重试评估判定；执行完成后主动触发一次Run状态聚合。
     * </p>
     * @param attemptId 目标Attempt ID
     * @param evidence 已缓存的回放证据包
     * @param documentChanges 文档变更证据
     * @param evaluatorVersionIds 需要重跑的评估器版本白名单，空集合=全部重跑
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

        // 临时切为评估中，清除Run完成标记，允许重算
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

        // 回写Attempt最终状态
        updateStatus(attempt, caseRun,
                error ? EvaluationAttemptStatus.EVALUATOR_FAILED : EvaluationAttemptStatus.COMPLETED);
        finish(attempt);

        // 重聚合整Run状态
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseRunEntity>()
                        .eq(EvaluationCaseRunEntity::getRunId, run.getId()));
        List<Long> currentAttemptIds = caseRuns.stream()
                .map(EvaluationCaseRunEntity::getCurrentAttemptId).toList();
        aggregate(run, attemptMapper.selectBatchIds(currentAttemptIds), caseRuns);
    }

    /**
     * 执行当前用例绑定的所有评估器
     * <p>特性：
     * 1. 支持 force 强制重跑，忽略已有EvaluationResult；非强制时跳过已存在成功结果；
     * 2. replayFailed=true时，只运行白名单评估器，其余SKIPPED；
     * 3. 每个评估器在独立线程池执行，带超时控制；
     * 4. 调用 {@link EvaluationResultWriteService#append} 原子写入评估结果记录；
     * 5. 埋点链路追踪、耗时指标统计。
     * </p>
     * @param attempt 用例尝试
     * @param caseRun 用例运行
     * @param evidence 回放证据包
     * @param documentChanges 文档变更证据
     * @param replayFailed 是否回放失败
     * @param force 是否强制重跑（忽略已存在结果）
     * @param targetEvaluatorVersionIds 指定评估器白名单，空=全部
     * @return true：至少一个评估器返回ERROR；false：全部SKIPPED/PASSED/FAILED
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

            // 不在指定重跑白名单内，跳过
            if (!targetEvaluatorVersionIds.isEmpty()
                    && !targetEvaluatorVersionIds.contains(binding.getEvaluatorVersionId())) {
                continue;
            }

            // 评估器版本不存在或未发布，标记为错误
            if (evaluator == null || !EvaluationVersionStatus.PUBLISHED.name().equals(evaluator.getStatus())) {
                error = true;
                continue;
            }

            // 非强制重跑且已存在结果，直接复用，不重复执行
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
                // 回放失败且评估器不在白名单 → SKIPPED
                if (replayFailed && !REPLAY_FAILURE_ALLOWED_EVALUATORS.contains(evaluator.getEvaluatorKey())) {
                    outcome = new DeterministicEvaluationOutcome(
                            EvaluationResultStatus.SKIPPED, "REPLAY_FAILED", "{\"reason\":\"REPLAY_FAILED\"}",
                            List.of(), List.of());
                } else {
                    // 优先级：binding上的预期值 > 用例版本默认预期值
                    String expected = binding.getExpectedJson() == null || binding.getExpectedJson().isBlank()
                            ? testCase.getExpectedJson() : binding.getExpectedJson();
                    outcome = executeEvaluator(evaluator, expected, evidence, documentChanges);
                }

                // 原子写入评估结果
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
     * 在独立线程池执行单个Evaluator，带超时控制
     * <p>异常分类：
     * - 超时 → 返回ERROR结果；
     * - 执行器内部异常(ExecutionException) → 包装为ERROR结果；
     * - 线程池拒绝、线程中断 → 向上抛出，属于基础设施异常，交由对账重试逻辑处理；
     * </p>
     * @param evaluator 评估器版本配置
     * @param expected 预期输出JSON
     * @param evidence 回放证据包
     * @param documentChanges 文档变更证据
     * @return 评估器输出结果
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

    /**
     * 构造评估器错误输出对象
     * @param reason 错误编码
     * @return 评估结果
     */
    private static DeterministicEvaluationOutcome evaluatorError(String reason) {
        return new DeterministicEvaluationOutcome(EvaluationResultStatus.ERROR, reason,
                "{\"reason\":\"" + reason + "\"}", List.of(), List.of());
    }

    /**
     * 判断分片内是否存在需要 document-change-validator 证据的评估器，用于按需拉取变更证据，减少IO
     */
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

    /**
     * 对外工具方法：判断指定用例版本 + 指定评估器集合是否需要文档变更证据（用于预加载/预校验）
     */
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
     * 查询该Attempt下某评估器版本最新一条EvaluationResult；按evaluationAttemptNo降序，再按ID降序
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
     * 聚合整Run顶层状态：基于所有当前Attempt状态，使用状态策略算出Run最终状态并落库；
     * 同步CaseRun状态，保持与当前Attempt状态一致。
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
        // 终态记录完成时间
        if (status == EvaluationRunStatus.COMPLETED || status == EvaluationRunStatus.COMPLETED_WITH_ERRORS
                || status == EvaluationRunStatus.CANCELED) {
            run.setFinishedAt(LocalDateTime.now());
        }
        runMapper.updateById(run);
        runMapper.update(null, new UpdateWrapper<EvaluationRunEntity>()
                .eq("id", run.getId())
                .set("pause_reason", null));

        // 同步CaseRun状态 = 当前Attempt状态
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
     * 标记回放任务异常失败，写入失败阶段与错误码，更新Attempt状态为REPLAY_FAILED
     */
    private void failReplay(EvaluationCaseAttemptEntity attempt, EvaluationCaseRunEntity caseRun, String code) {
        attempt.setFailureStage("RECONCILIATION");
        attempt.setFailureCode(code);
        updateStatus(attempt, caseRun, EvaluationAttemptStatus.REPLAY_FAILED);
        finish(attempt);
    }

    /**
     * 原子同步更新Attempt与CaseRun状态、更新时间
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
     * 标记Attempt完成时刻
     */
    private void finish(EvaluationCaseAttemptEntity attempt) {
        attempt.setFinishedAt(LocalDateTime.now());
        attemptMapper.updateById(attempt);
    }

    /**
     * 将Run置为PAUSED并写入暂停原因
     */
    private void pause(EvaluationRunEntity run, EvaluationPauseReason reason) {
        run.setStatus(EvaluationRunStatus.PAUSED.name());
        run.setPauseReason(reason.name());
        run.setUpdatedAt(LocalDateTime.now());
        runMapper.updateById(run);
    }

    /**
     * 累加对账失败计数；达到阈值后暂停Run，否则仅打日志等待下一轮定时重试，容忍瞬时抖动
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
     * 判断Run是否为顶层终态，对账Job不再处理
     */
    private static boolean terminalRun(String status) {
        return EvaluationRunStatus.COMPLETED.name().equals(status)
                || EvaluationRunStatus.COMPLETED_WITH_ERRORS.name().equals(status)
                || EvaluationRunStatus.CANCELED.name().equals(status)
                || EvaluationRunStatus.FAILED.name().equals(status);
    }

    /**
     * Feign统一解包工具：非成功响应抛业务异常
     */
    private static <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "task-service 调用失败" : result.message());
        }
        return result.data();
    }
}
