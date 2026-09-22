package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.ReplayBatchCreateDTO;
import com.agentdoc.common.feign.dto.ReplayBatchItemDTO;
import com.agentdoc.common.feign.vo.ReplayBatchCreateVO;
import com.agentdoc.common.feign.dto.EvaluationTaskBatchQueryDTO;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityRenewDTO;
import com.agentdoc.common.feign.vo.EvaluationTaskCancelVO;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangeEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationWorkerCapabilityVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.constant.EvaluationConstant;
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
import com.agentdoc.evaluation.pojo.dto.EvaluationRunCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationRunResumeDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationRetryDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetCaseEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseVersionEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseEntity;
import com.agentdoc.evaluation.pojo.vo.EvaluationCaseRunVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationRunVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationFeedbackVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.Objects;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;

/**
 * EvaluationRun 创建、派发与查询应用服务。
 * 对外暴露评估任务的创建、详情查询、恢复、取消、评估器重试、Replay重试接口；
 * 负责权限校验、参数解析、组装Replay批量任务，委托持久化服务与状态处理器完成底层状态流转。
 */
@Service
@RequiredArgsConstructor
public class EvaluationRunService {

    private final EvaluationDatasetVersionMapper datasetVersionMapper;
    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationDatasetCaseMapper datasetCaseMapper;
    private final EvaluationTestCaseVersionMapper testCaseVersionMapper;
    private final EvaluationTestCaseMapper testCaseMapper;
    private final EvaluationRunMapper runMapper;
    private final EvaluationFeedbackMapper feedbackMapper;
    private final EvaluationCaseRunMapper caseRunMapper;
    private final EvaluationCaseAttemptMapper attemptMapper;
    private final EvaluationRunPersistenceService persistenceService;
    private final SpaceAccessService spaceAccessService;
    private final TaskFeign taskFeign;
    private final WorkerCapabilitySegmentService segmentService;
    private final EvaluationRunProcessor runProcessor;
    private final EvaluationRuntimeProperties runtimeProperties;

    /**
     * 创建评估任务Run：解析用例、权限校验、落库Run/CaseRun/Attempt，调用task-feign创建批量Replay任务
     * 若Replay创建异常，自动将Run置为PAUSED，返回VO
     */
    public EvaluationRunVO create(EvaluationRunCreateDTO request) {
        if (!runtimeProperties.isRunCreationEnabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "EvaluationRun 创建已由运维门禁暂停");
        }
        requireOneTarget(request);
        spaceAccessService.requirePermission(request.spaceId(), EVALUATION_RUN);
        List<EvaluationTestCaseVersionEntity> caseVersions = resolveCaseVersions(request);
        EvaluationRunPersistenceService.RunDraft draft = persistenceService.create(
                request.spaceId(), request.datasetVersionId(), request.singleTestCaseVersionId(),
                AuthUtils.getUserIdOrException(), caseVersions.stream().map(EvaluationTestCaseVersionEntity::getId).toList());
        Map<Long, EvaluationTestCaseVersionEntity> caseById = caseVersions.stream()
                .collect(Collectors.toMap(EvaluationTestCaseVersionEntity::getId, Function.identity()));
        List<ReplayBatchItemDTO> items = draft.cases().stream()
                .map(item -> new ReplayBatchItemDTO(caseById.get(item.caseRun().getTestCaseVersionId()).getSourceTaskId(),
                        EvaluationRunPersistenceService.requestKey(item.attempt().getId())))
                .toList();
        try {
            ReplayBatchCreateVO response = requireData(taskFeign.createReplayBatch(new ReplayBatchCreateDTO(
                    draft.run().getId(), request.spaceId(), request.workerCapabilityTtlSeconds(), items)));
            draft = persistenceService.attachDispatch(draft, response);
        } catch (RuntimeException exception) {
            draft = persistenceService.pauseDispatch(draft);
        }
        return toVO(draft);
    }

    /**
     * 查询Run详情，校验空间权限，加载CaseRun与当前Attempt，组装VO返回
     */
    public EvaluationRunVO detail(Long id) {
        EvaluationRunEntity run = runMapper.selectById(id);
        if (run == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "EvaluationRun 不存在");
        }
        spaceAccessService.requirePermission(run.getSpaceId(), EVALUATION_READ);
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseRunEntity>()
                        .eq(EvaluationCaseRunEntity::getRunId, id)
                        .orderByAsc(EvaluationCaseRunEntity::getId));
        Map<Long, EvaluationCaseAttemptEntity> attempts = attemptMapper.selectBatchIds(caseRuns.stream()
                        .map(EvaluationCaseRunEntity::getCurrentAttemptId).toList()).stream()
                .collect(Collectors.toMap(EvaluationCaseAttemptEntity::getId, Function.identity()));
        Map<Long, List<EvaluationFeedbackVO>> feedbackByCaseRun = feedbackMapper.selectList(
                        new LambdaQueryWrapper<EvaluationFeedbackEntity>()
                                .eq(EvaluationFeedbackEntity::getRunId, id)
                                .isNotNull(EvaluationFeedbackEntity::getCaseRunId)
                                .orderByAsc(EvaluationFeedbackEntity::getId)).stream()
                .collect(Collectors.groupingBy(EvaluationFeedbackEntity::getCaseRunId,
                        Collectors.mapping(EvaluationFeedbackVO::from, Collectors.toList())));
        List<EvaluationCaseRunVO> cases = caseRuns.stream()
                .map(caseRun -> EvaluationCaseRunVO.from(caseRun, requireAttempt(attempts, caseRun),
                        feedbackByCaseRun.getOrDefault(caseRun.getId(), List.of())))
                .toList();
        return EvaluationRunVO.from(run, cases);
    }

    /**
     * 恢复PAUSED状态的Run：重新构建Replay批量任务，使用下一个batchNo绑定分片；
     * 若恢复前已标记取消，则恢复完成后自动执行cancel
     */
    public EvaluationRunVO resume(Long id, EvaluationRunResumeDTO request) {
        EvaluationRunEntity run = requireRun(id);
        spaceAccessService.requirePermission(run.getSpaceId(), EVALUATION_RUN);
        if (!com.agentdoc.evaluation.enums.EvaluationRunStatus.PAUSED.name().equals(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有 PAUSED EvaluationRun 可以恢复");
        }
        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseRunEntity>()
                        .eq(EvaluationCaseRunEntity::getRunId, id).orderByAsc(EvaluationCaseRunEntity::getId));
        Map<Long, EvaluationCaseAttemptEntity> attempts = attemptMapper.selectBatchIds(caseRuns.stream()
                        .map(EvaluationCaseRunEntity::getCurrentAttemptId).toList()).stream()
                .collect(Collectors.toMap(EvaluationCaseAttemptEntity::getId, Function.identity()));
        Map<Long, EvaluationTestCaseVersionEntity> cases = testCaseVersionMapper.selectBatchIds(caseRuns.stream()
                        .map(EvaluationCaseRunEntity::getTestCaseVersionId).toList()).stream()
                .collect(Collectors.toMap(EvaluationTestCaseVersionEntity::getId, Function.identity()));
        List<EvaluationRunPersistenceService.RunCaseDraft> drafts = new ArrayList<>();
        List<ReplayBatchItemDTO> items = new ArrayList<>();
        for (EvaluationCaseRunEntity caseRun : caseRuns) {
            EvaluationCaseAttemptEntity attempt = requireAttempt(attempts, caseRun);
            EvaluationTestCaseVersionEntity testCase = cases.get(caseRun.getTestCaseVersionId());
            if (testCase == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "EvaluationRun 的 TestCaseVersion 已不可用");
            }
            drafts.add(new EvaluationRunPersistenceService.RunCaseDraft(caseRun, attempt));
            items.add(new ReplayBatchItemDTO(testCase.getSourceTaskId(),
                    EvaluationRunPersistenceService.requestKey(attempt.getId())));
        }
        EvaluationRunPersistenceService.RunDraft draft = new EvaluationRunPersistenceService.RunDraft(run, drafts);
        try {
            ReplayBatchCreateVO response = requireData(taskFeign.createReplayBatch(new ReplayBatchCreateDTO(
                    run.getId(), run.getSpaceId(), request.workerCapabilityTtlSeconds(), items)));
            draft = persistenceService.attachDispatch(draft, response, segmentService.nextBatchNo(run.getId()));
        } catch (RuntimeException exception) {
            draft = persistenceService.pauseDispatch(draft);
        }
        if (Boolean.TRUE.equals(run.getCancelRequested())
                && !com.agentdoc.evaluation.enums.EvaluationRunStatus.PAUSED.name().equals(run.getStatus())) {
            return cancel(id);
        }
        return toVO(draft);
    }

    /**
     * 取消评估Run：标记cancelRequested=true，状态置为CANCEL_PENDING；
     * 按能力分片调用taskFeign批量取消Replay任务；调用异常则将Run置为PAUSED
     */
    public EvaluationRunVO cancel(Long id) {
        EvaluationRunEntity run = requireRun(id);
        spaceAccessService.requirePermission(run.getSpaceId(), EVALUATION_RUN);
        if (terminalRun(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "终态 EvaluationRun 不能取消");
        }
        run.setCancelRequested(true);
        run.setStatus(com.agentdoc.evaluation.enums.EvaluationRunStatus.CANCEL_PENDING.name());
        runMapper.updateById(run);
        List<EvaluationCaseAttemptEntity> allAttempts = attemptMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseAttemptEntity>()
                        .eq(EvaluationCaseAttemptEntity::getRunId, id));
        Map<Long, List<EvaluationCaseAttemptEntity>> bySegment = allAttempts.stream()
                .filter(attempt -> attempt.getCapabilitySegmentId() != null && attempt.getReplayTaskId() != null)
                .collect(Collectors.groupingBy(EvaluationCaseAttemptEntity::getCapabilitySegmentId));
        try {
            for (List<EvaluationCaseAttemptEntity> segmentAttempts : bySegment.values()) {
                String capability = segmentService.requireActiveCapability(
                        segmentAttempts.getFirst().getCapabilitySegmentId(), run.getId(), run.getSpaceId());
                List<Long> taskIds = segmentAttempts.stream().map(EvaluationCaseAttemptEntity::getReplayTaskId)
                        .sorted().toList();
                List<EvaluationTaskCancelVO> results = requireData(taskFeign.cancelEvaluationTasks(capability,
                        new EvaluationTaskBatchQueryDTO(run.getId(), run.getSpaceId(), taskIds)));
                if (results.stream().anyMatch(result -> !result.accepted())) {
                    run.setStatus(com.agentdoc.evaluation.enums.EvaluationRunStatus.CANCEL_PENDING.name());
                }
            }
        } catch (RuntimeException exception) {
            run.setStatus(com.agentdoc.evaluation.enums.EvaluationRunStatus.PAUSED.name());
            run.setPauseReason(com.agentdoc.evaluation.enums.EvaluationPauseReason.AUTHORIZATION_EXPIRED.name());
            runMapper.updateById(run);
        }
        return detail(id);
    }

    /**
     * 评估器重试：仅支持COMPLETED / EVALUATOR_FAILED的Attempt；
     * 获取证据，调用runProcessor.retryEvaluation强制重跑指定评估器版本，返回最新Run详情
     */
    public EvaluationRunVO retryEvaluation(Long attemptId, EvaluationRetryDTO request) {
        EvaluationCaseAttemptEntity attempt = attemptMapper.selectById(attemptId);
        EvaluationCaseRunEntity caseRun = attempt == null ? null : caseRunMapper.selectById(attempt.getCaseRunId());
        EvaluationRunEntity run = attempt == null ? null : runMapper.selectById(attempt.getRunId());
        if (attempt == null || caseRun == null || run == null
                || !attempt.getId().equals(caseRun.getCurrentAttemptId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前 CaseAttempt 不存在");
        }
        spaceAccessService.requirePermission(run.getSpaceId(), EVALUATION_RUN);
        var status = com.agentdoc.evaluation.enums.EvaluationAttemptStatus.valueOf(attempt.getStatus());
        if (status != com.agentdoc.evaluation.enums.EvaluationAttemptStatus.COMPLETED
                && status != com.agentdoc.evaluation.enums.EvaluationAttemptStatus.EVALUATOR_FAILED) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前 CaseAttempt 不允许重试 Evaluator");
        }
        if (request == null || request.workerCapabilityTtlSeconds() == null
                || attempt.getReplayTaskId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Evaluator 重试参数或 Replay Task 无效");
        }
        Set<Long> targets = request.evaluatorVersionIds() == null ? Set.of()
                : request.evaluatorVersionIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (targets.size() > 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次最多重试 20 个 EvaluatorVersion");
        }
        renewWorkerCapability(run, attempt, request.workerCapabilityTtlSeconds());
        EvaluationEvidenceBundleVO evidence = evidenceForAttempt(run, attempt);
        List<EvaluationDocumentChangeEvidenceVO> documentChanges =
                runProcessor.requiresDocumentChangeEvidence(caseRun.getTestCaseVersionId(), targets)
                        ? documentChangesForAttempt(run, attempt) : List.of();
        runProcessor.retryEvaluation(attempt.getId(), evidence, documentChanges, targets);
        return detail(run.getId());
    }

    /**
     * Replay重试：为单个CaseRun新建一条Attempt，发起新一轮Replay任务；
     * 仅允许非活跃、未标记取消的CaseRun执行
     */
    public EvaluationRunVO retryReplay(Long caseRunId, EvaluationRunResumeDTO request) {
        EvaluationCaseRunEntity caseRun = caseRunMapper.selectById(caseRunId);
        EvaluationRunEntity run = caseRun == null ? null : runMapper.selectById(caseRun.getRunId());
        EvaluationCaseAttemptEntity previous = caseRun == null ? null
                : attemptMapper.selectById(caseRun.getCurrentAttemptId());
        if (caseRun == null || run == null || previous == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "CaseRun 不存在");
        }
        spaceAccessService.requirePermission(run.getSpaceId(), EVALUATION_RUN);
        if (Boolean.TRUE.equals(run.getCancelRequested())
                || com.agentdoc.evaluation.enums.EvaluationAttemptStatus.valueOf(previous.getStatus()).active()) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前 CaseRun 不允许重试 Replay");
        }
        EvaluationTestCaseVersionEntity testCase = testCaseVersionMapper.selectById(caseRun.getTestCaseVersionId());
        requirePublishedCase(testCase, run.getSpaceId());
        var retry = persistenceService.createReplayRetry(run, caseRun, previous);
        EvaluationRunPersistenceService.RunDraft draft = new EvaluationRunPersistenceService.RunDraft(
                run, List.of(retry));
        try {
            ReplayBatchCreateVO response = requireData(taskFeign.createReplayBatch(new ReplayBatchCreateDTO(
                    run.getId(), run.getSpaceId(), request.workerCapabilityTtlSeconds(),
                    List.of(new ReplayBatchItemDTO(testCase.getSourceTaskId(),
                            EvaluationRunPersistenceService.requestKey(retry.attempt().getId()))))));
            persistenceService.attachDispatch(draft, response, segmentService.nextBatchNo(run.getId()));
        } catch (RuntimeException exception) {
            persistenceService.pauseDispatch(draft);
        }
        return detail(run.getId());
    }

    /**
     * 根据Attempt获取对应的评估证据Bundle：通过capability分片查询，过滤出当前attempt对应的task证据
     */
    private EvaluationEvidenceBundleVO evidenceForAttempt(EvaluationRunEntity run,
                                                          EvaluationCaseAttemptEntity target) {
        List<EvaluationCaseAttemptEntity> segmentAttempts = attemptMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseAttemptEntity>()
                        .eq(EvaluationCaseAttemptEntity::getCapabilitySegmentId, target.getCapabilitySegmentId()));
        List<Long> taskIds = segmentAttempts.stream().map(EvaluationCaseAttemptEntity::getReplayTaskId)
                .filter(Objects::nonNull).sorted().toList();
        String capability = segmentService.requireActiveCapability(target.getCapabilitySegmentId(),
                run.getId(), run.getSpaceId());
        return requireData(taskFeign.queryEvaluationEvidence(capability,
                new EvaluationTaskBatchQueryDTO(run.getId(), run.getSpaceId(), taskIds))).stream()
                .filter(value -> target.getReplayTaskId().equals(value.taskId())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "Replay 评估事实不可用"));
    }

    private List<EvaluationDocumentChangeEvidenceVO> documentChangesForAttempt(
            EvaluationRunEntity run, EvaluationCaseAttemptEntity target) {
        List<EvaluationCaseAttemptEntity> segmentAttempts = attemptMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseAttemptEntity>()
                        .eq(EvaluationCaseAttemptEntity::getCapabilitySegmentId, target.getCapabilitySegmentId()));
        List<Long> taskIds = segmentAttempts.stream().map(EvaluationCaseAttemptEntity::getReplayTaskId)
                .filter(Objects::nonNull).sorted().toList();
        String capability = segmentService.requireActiveCapability(target.getCapabilitySegmentId(),
                run.getId(), run.getSpaceId());
        return requireData(taskFeign.queryEvaluationDocumentChanges(capability,
                new EvaluationTaskBatchQueryDTO(run.getId(), run.getSpaceId(), taskIds))).stream()
                .filter(value -> target.getReplayTaskId().equals(value.taskId())).toList();
    }

    private void renewWorkerCapability(EvaluationRunEntity run, EvaluationCaseAttemptEntity attempt,
                                       Long ttlSeconds) {
        List<Long> taskIds = List.of(attempt.getReplayTaskId());
        EvaluationWorkerCapabilityVO response = requireData(taskFeign.renewEvaluationWorkerCapability(
                new EvaluationWorkerCapabilityRenewDTO(run.getId(), run.getSpaceId(), ttlSeconds, taskIds)));
        String expectedHash = StableSnapshotUtils.snapshotHash(1, taskIds);
        if (!run.getId().equals(response.runId()) || !run.getSpaceId().equals(response.spaceId())
                || !expectedHash.equals(response.taskIdsHash())) {
            throw new BusinessException(ErrorCode.CONFLICT, "WorkerCapability 续签响应身份不匹配");
        }
        var segment = segmentService.append(run.getId(), run.getSpaceId(),
                segmentService.nextBatchNo(run.getId()), response.taskIdsHash(),
                response.workerCapability(), response.expiresAt());
        attempt.setCapabilitySegmentId(segment.getId());
        attemptMapper.updateById(attempt);
    }

    /**
     * 解析创建请求中的用例列表：支持单TestCaseVersion或DatasetVersion批量用例，校验用例已发布、同空间
     */
    private List<EvaluationTestCaseVersionEntity> resolveCaseVersions(EvaluationRunCreateDTO request) {
        if (request.singleTestCaseVersionId() != null) {
            EvaluationTestCaseVersionEntity version = testCaseVersionMapper.selectById(request.singleTestCaseVersionId());
            requirePublishedCase(version, request.spaceId());
            return List.of(version);
        }
        EvaluationDatasetVersionEntity dataset = datasetVersionMapper.selectById(request.datasetVersionId());
        EvaluationDatasetEntity datasetResource = dataset == null ? null : datasetMapper.selectById(dataset.getDatasetId());
        if (dataset == null || !request.spaceId().equals(dataset.getSpaceId())
                || !EvaluationVersionStatus.PUBLISHED.name().equals(dataset.getStatus())
                || datasetResource == null || Boolean.TRUE.equals(datasetResource.getArchived())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能运行同空间已发布 DatasetVersion");
        }
        List<EvaluationDatasetCaseEntity> bindings = datasetCaseMapper.selectList(
                new LambdaQueryWrapper<EvaluationDatasetCaseEntity>()
                        .eq(EvaluationDatasetCaseEntity::getDatasetVersionId, dataset.getId())
                        .eq(EvaluationDatasetCaseEntity::getEnabled, true)
                        .orderByAsc(EvaluationDatasetCaseEntity::getSortOrder, EvaluationDatasetCaseEntity::getId));
        if (bindings.isEmpty() || bindings.size() > EvaluationConstant.MAX_DATASET_CASE_COUNT) {
            throw new BusinessException(ErrorCode.CONFLICT, "DatasetVersion 启用用例数量无效");
        }
        Map<Long, EvaluationTestCaseVersionEntity> versions = testCaseVersionMapper.selectBatchIds(bindings.stream()
                        .map(EvaluationDatasetCaseEntity::getTestCaseVersionId).toList()).stream()
                .collect(Collectors.toMap(EvaluationTestCaseVersionEntity::getId, Function.identity()));
        return bindings.stream().map(binding -> {
            EvaluationTestCaseVersionEntity version = versions.get(binding.getTestCaseVersionId());
            requirePublishedCase(version, request.spaceId());
            return version;
        }).toList();
    }

    /**
     * 校验TestCaseVersion存在、同空间、已发布
     */
    private void requirePublishedCase(EvaluationTestCaseVersionEntity version, Long spaceId) {
        EvaluationTestCaseEntity testCase = version == null ? null : testCaseMapper.selectById(version.getTestCaseId());
        if (version == null || !spaceId.equals(version.getSpaceId())
                || !EvaluationVersionStatus.PUBLISHED.name().equals(version.getStatus())
                || testCase == null || Boolean.TRUE.equals(testCase.getArchived())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能运行同空间已发布 TestCaseVersion");
        }
    }

    /**
     * 创建参数校验：datasetVersionId / singleTestCaseVersionId 二选一必填
     */
    private void requireOneTarget(EvaluationRunCreateDTO request) {
        if ((request.datasetVersionId() == null) == (request.singleTestCaseVersionId() == null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "DatasetVersion 与单 TestCaseVersion 必须且只能指定一个");
        }
    }

    /**
     * RunDraft转对外VO
     */
    private EvaluationRunVO toVO(EvaluationRunPersistenceService.RunDraft draft) {
        return EvaluationRunVO.from(draft.run(), draft.cases().stream()
                .map(item -> EvaluationCaseRunVO.from(item.caseRun(), item.attempt())).toList());
    }

    /**
     * 获取CaseRun对应的当前Attempt，不存在抛异常
     */
    private EvaluationCaseAttemptEntity requireAttempt(Map<Long, EvaluationCaseAttemptEntity> attempts,
                                                       EvaluationCaseRunEntity caseRun) {
        EvaluationCaseAttemptEntity attempt = attempts.get(caseRun.getCurrentAttemptId());
        if (attempt == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "CaseRun 当前 Attempt 不存在");
        }
        return attempt;
    }

    /**
     * Feign返回结果统一解包，非成功code抛业务异常
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "task-service 调用失败" : result.message());
        }
        return result.data();
    }

    /**
     * 根据ID查询Run，不存在抛NOT_FOUND
     */
    private EvaluationRunEntity requireRun(Long id) {
        EvaluationRunEntity run = runMapper.selectById(id);
        if (run == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "EvaluationRun 不存在");
        }
        return run;
    }

    /**
     * 判断Run是否为终态，终态不可取消
     */
    private boolean terminalRun(String status) {
        return com.agentdoc.evaluation.enums.EvaluationRunStatus.COMPLETED.name().equals(status)
                || com.agentdoc.evaluation.enums.EvaluationRunStatus.COMPLETED_WITH_ERRORS.name().equals(status)
                || com.agentdoc.evaluation.enums.EvaluationRunStatus.CANCELED.name().equals(status)
                || com.agentdoc.evaluation.enums.EvaluationRunStatus.FAILED.name().equals(status);
    }
}
