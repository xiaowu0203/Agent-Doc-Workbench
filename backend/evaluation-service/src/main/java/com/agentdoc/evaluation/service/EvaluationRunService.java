package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.dto.ReplayBatchCreateDTO;
import com.agentdoc.common.feign.dto.ReplayBatchItemDTO;
import com.agentdoc.common.feign.vo.*;
import com.agentdoc.common.feign.dto.EvaluationTaskBatchQueryDTO;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityRenewDTO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.constant.EvaluationConstant;
import com.agentdoc.evaluation.config.EvaluationRuntimeProperties;
import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationPauseReason;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import com.agentdoc.evaluation.enums.EvaluationVersionStatus;
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
import java.util.Set;
import java.util.Objects;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;

/**
 * EvaluationRun 创建、派发、查询、运维操作应用服务（Facade）。
 * <p>对外暴露评估任务全量业务接口：创建、查询详情、恢复、取消、评估器重试、Replay回放重试。
 * 本层职责：
 * <ul>
 *     <li>空间权限校验、请求参数合法性校验；</li>
 *     <li>解析数据集版本/单用例，加载待运行TestCaseVersion；</li>
 *     <li>组装Replay批量任务DTO，调用task-service Feign下发回放；</li>
 *     <li>异常分支兜底：下发失败自动置PAUSED；</li>
 *     <li>委托 {@link EvaluationRunPersistenceService} 完成Run/CaseRun/Attempt落库；</li>
 *     <li>委托 {@link EvaluationRunProcessor} 执行评估器重跑与状态聚合；</li>
 * </ul>
 * <b>注意：本服务不实现定时对账、轮询Replay状态机逻辑，该能力由 {@link EvaluationRunProcessor} 定时Job承载。</b>
 * </p>
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
     * 创建评估任务Run
     * <p>流程：
     * 1. 校验全局开关；
     * 2. 二选一参数校验：datasetVersionId / singleTestCaseVersionId；
     * 3. 空间权限校验 EVALUATION_RUN；
     * 4. 解析用例集合，校验所有TestCaseVersion已发布、未归档、同空间；
     * 5. 调用persistenceService创建Run/CaseRun/Attempt草稿Draft；
     * 6. 组装批量Replay请求，调用taskFeign下发回放任务；
     * 7. 下发成功：绑定dispatch信息到Draft；下发异常：自动置为PAUSED；
     * 8. 转换为VO返回。
     * </p>
     * @param request 创建参数DTO
     * @return 新建Run视图对象
     */
    public EvaluationRunVO create(EvaluationRunCreateDTO request) {
        if (!runtimeProperties.isRunCreationEnabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "EvaluationRun 创建已由运维门禁暂停");
        }
        requireOneTarget(request);
        spaceAccessService.requirePermission(request.spaceId(), EVALUATION_RUN);

        List<EvaluationTestCaseVersionEntity> caseVersions = resolveCaseVersions(request);

        EvaluationRunPersistenceService.RunDraft draft = persistenceService.create(
                request.spaceId(),
                request.datasetVersionId(),
                request.singleTestCaseVersionId(),
                AuthUtils.getUserIdOrException(),
                caseVersions.stream().map(EvaluationTestCaseVersionEntity::getId).toList()
        );

        Map<Long, EvaluationTestCaseVersionEntity> caseById = caseVersions.stream()
                .collect(Collectors.toMap(EvaluationTestCaseVersionEntity::getId, Function.identity()));

        List<ReplayBatchItemDTO> items = draft.cases().stream()
                .map(item -> new ReplayBatchItemDTO(
                        caseById.get(item.caseRun().getTestCaseVersionId()).getSourceTaskId(),
                        EvaluationRunPersistenceService.requestKey(item.attempt().getId())
                ))
                .toList();

        try {
            ReplayBatchCreateVO response = requireData(taskFeign.createReplayBatch(new ReplayBatchCreateDTO(
                    draft.run().getId(),
                    request.spaceId(),
                    request.workerCapabilityTtlSeconds(),
                    items
            )));
            draft = persistenceService.attachDispatch(draft, response);
        } catch (RuntimeException exception) {
            // Replay下发失败，兜底暂停
            draft = persistenceService.pauseDispatch(draft);
        }

        return toVO(draft);
    }

    /**
     * 查询Run详情，包含所有CaseRun、当前Attempt、人工反馈列表
     * @param id run主键ID
     * @return 完整Run视图
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
                        .orderByAsc(EvaluationCaseRunEntity::getId)
        );

        Map<Long, EvaluationCaseAttemptEntity> attempts = attemptMapper.selectBatchIds(
                        caseRuns.stream().map(EvaluationCaseRunEntity::getCurrentAttemptId).toList())
                .stream()
                .collect(Collectors.toMap(EvaluationCaseAttemptEntity::getId, Function.identity()));

        // 加载人工反馈，按caseRun分组
        Map<Long, List<EvaluationFeedbackVO>> feedbackByCaseRun = feedbackMapper.selectList(
                        new LambdaQueryWrapper<EvaluationFeedbackEntity>()
                                .eq(EvaluationFeedbackEntity::getRunId, id)
                                .isNotNull(EvaluationFeedbackEntity::getCaseRunId)
                                .orderByAsc(EvaluationFeedbackEntity::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        EvaluationFeedbackEntity::getCaseRunId,
                        Collectors.mapping(EvaluationFeedbackVO::from, Collectors.toList())
                ));

        List<EvaluationCaseRunVO> cases = caseRuns.stream()
                .map(caseRun -> EvaluationCaseRunVO.from(
                        caseRun,
                        requireAttempt(attempts, caseRun),
                        feedbackByCaseRun.getOrDefault(caseRun.getId(), List.of())
                ))
                .toList();

        return EvaluationRunVO.from(run, cases);
    }

    /**
     * 恢复已暂停PAUSED的Run：重新下发批量Replay任务，分配新batchNo与分片
     * <p>边界：如果恢复前Run已经标记cancelRequested，下发成功后自动触发cancel流程</p>
     * @param id runId
     * @param request 恢复参数（包含capability ttl）
     * @return 最新RunVO
     */
    public EvaluationRunVO resume(Long id, EvaluationRunResumeDTO request) {
        EvaluationRunEntity run = requireRun(id);
        spaceAccessService.requirePermission(run.getSpaceId(), EVALUATION_RUN);

        if (!EvaluationRunStatus.PAUSED.name().equals(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有 PAUSED EvaluationRun 可以恢复");
        }

        List<EvaluationCaseRunEntity> caseRuns = caseRunMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseRunEntity>()
                        .eq(EvaluationCaseRunEntity::getRunId, id)
                        .orderByAsc(EvaluationCaseRunEntity::getId)
        );

        Map<Long, EvaluationCaseAttemptEntity> attempts = attemptMapper.selectBatchIds(
                        caseRuns.stream().map(EvaluationCaseRunEntity::getCurrentAttemptId).toList())
                .stream()
                .collect(Collectors.toMap(EvaluationCaseAttemptEntity::getId, Function.identity()));

        Map<Long, EvaluationTestCaseVersionEntity> cases = testCaseVersionMapper.selectBatchIds(
                        caseRuns.stream().map(EvaluationCaseRunEntity::getTestCaseVersionId).toList())
                .stream()
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
            items.add(new ReplayBatchItemDTO(
                    testCase.getSourceTaskId(),
                    EvaluationRunPersistenceService.requestKey(attempt.getId())
            ));
        }

        EvaluationRunPersistenceService.RunDraft draft = new EvaluationRunPersistenceService.RunDraft(run, drafts);
        try {
            ReplayBatchCreateVO response = requireData(taskFeign.createReplayBatch(new ReplayBatchCreateDTO(
                    run.getId(),
                    run.getSpaceId(),
                    request.workerCapabilityTtlSeconds(),
                    items
            )));
            // resume 使用新 batchNo，避免和之前分片冲突
            draft = persistenceService.attachDispatch(draft, response, segmentService.nextBatchNo(run.getId()));
        } catch (RuntimeException exception) {
            draft = persistenceService.pauseDispatch(draft);
        }

        // 如果恢复前已经请求取消，恢复下发完成后自动执行取消逻辑
        if (Boolean.TRUE.equals(run.getCancelRequested())
                && !EvaluationRunStatus.PAUSED.name().equals(draft.run().getStatus())) {
            return cancel(id);
        }
        return toVO(draft);
    }

    /**
     * 取消Run：标记cancelRequested=true，状态切CANCEL_PENDING；按分片批量调用task服务取消回放任务
     * <p>Feign调用异常时兜底将Run置PAUSED，防止无限等待</p>
     * @param id runId
     * @return Run最新详情
     */
    public EvaluationRunVO cancel(Long id) {
        EvaluationRunEntity run = requireRun(id);
        spaceAccessService.requirePermission(run.getSpaceId(), EVALUATION_RUN);

        if (terminalRun(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "终态 EvaluationRun 不能取消");
        }

        run.setCancelRequested(true);
        run.setStatus(EvaluationRunStatus.CANCEL_PENDING.name());
        runMapper.updateById(run);

        List<EvaluationCaseAttemptEntity> allAttempts = attemptMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseAttemptEntity>()
                        .eq(EvaluationCaseAttemptEntity::getRunId, id)
        );

        Map<Long, List<EvaluationCaseAttemptEntity>> bySegment = allAttempts.stream()
                .filter(attempt -> attempt.getCapabilitySegmentId() != null && attempt.getReplayTaskId() != null)
                .collect(Collectors.groupingBy(EvaluationCaseAttemptEntity::getCapabilitySegmentId));

        try {
            for (List<EvaluationCaseAttemptEntity> segmentAttempts : bySegment.values()) {
                String capability = segmentService.requireActiveCapability(
                        segmentAttempts.getFirst().getCapabilitySegmentId(),
                        run.getId(),
                        run.getSpaceId()
                );
                List<Long> taskIds = segmentAttempts.stream()
                        .map(EvaluationCaseAttemptEntity::getReplayTaskId)
                        .sorted()
                        .toList();
                List<EvaluationTaskCancelVO> results = requireData(taskFeign.cancelEvaluationTasks(capability,
                        new EvaluationTaskBatchQueryDTO(run.getId(), run.getSpaceId(), taskIds)));
                if (results.stream().anyMatch(result -> !result.accepted())) {
                    run.setStatus(com.agentdoc.evaluation.enums.EvaluationRunStatus.CANCEL_PENDING.name());
                }
            }
        } catch (RuntimeException exception) {
            // 取消RPC失败，暂停Run，留给人工干预
            run.setStatus(EvaluationRunStatus.PAUSED.name());
            run.setPauseReason(EvaluationPauseReason.AUTHORIZATION_EXPIRED.name());
            runMapper.updateById(run);
        }

        return detail(id);
    }

    /**
     * 评估器重试（不重跑Replay，直接复用已有的回放证据）
     * <p>适用：Attempt状态为 COMPLETED / EVALUATOR_FAILED；重新执行指定评估器版本，写入新EvaluationResult，然后聚合Run状态</p>
     * @param attemptId 目标Attempt主键
     * @param request 重试参数（评估器版本列表、capability续期TTL）
     * @return Run最新详情
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

        EvaluationAttemptStatus status = EvaluationAttemptStatus.valueOf(attempt.getStatus());
        if (status != EvaluationAttemptStatus.COMPLETED && status != EvaluationAttemptStatus.EVALUATOR_FAILED) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前 CaseAttempt 不允许重试 Evaluator");
        }

        if (request == null || request.workerCapabilityTtlSeconds() == null || attempt.getReplayTaskId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Evaluator 重试参数或 Replay Task 无效");
        }

        Set<Long> targets = request.evaluatorVersionIds() == null ? Set.of()
                : request.evaluatorVersionIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (targets.size() > 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次最多重试 20 个 EvaluatorVersion");
        }

        // 续期capability分片凭证，防止证据查询鉴权过期
        renewWorkerCapability(run, attempt, request.workerCapabilityTtlSeconds());
        EvaluationEvidenceBundleVO evidence = evidenceForAttempt(run, attempt);

        List<EvaluationDocumentChangeEvidenceVO> documentChanges =
                runProcessor.requiresDocumentChangeEvidence(caseRun.getTestCaseVersionId(), targets)
                        ? documentChangesForAttempt(run, attempt)
                        : List.of();

        // 委托Processor执行评估器重跑+状态聚合
        runProcessor.retryEvaluation(attempt.getId(), evidence, documentChanges, targets);
        return detail(run.getId());
    }

    /**
     * Replay回放重试：为CaseRun新建一条Attempt记录，发起一轮全新回放任务
     * <p>区别于retryEvaluation：这个会重新跑回放；retryEvaluation只重跑评估逻辑，复用回放结果</p>
     * @param caseRunId 目标CaseRun主键
     * @param request 恢复/重试参数
     * @return Run最新详情
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

        // 已经标记取消 或 当前Attempt还在活跃中，禁止新建回放重试
        if (Boolean.TRUE.equals(run.getCancelRequested()) || EvaluationAttemptStatus.valueOf(previous.getStatus()).active()) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前 CaseRun 不允许重试 Replay");
        }

        EvaluationTestCaseVersionEntity testCase = testCaseVersionMapper.selectById(caseRun.getTestCaseVersionId());
        requirePublishedCase(testCase, run.getSpaceId());

        EvaluationRunPersistenceService.RunCaseDraft retryDraft = persistenceService.createReplayRetry(run, caseRun, previous);
        EvaluationRunPersistenceService.RunDraft draft = new EvaluationRunPersistenceService.RunDraft(run, List.of(retryDraft));

        try {
            ReplayBatchCreateVO response = requireData(taskFeign.createReplayBatch(new ReplayBatchCreateDTO(
                    run.getId(),
                    run.getSpaceId(),
                    request.workerCapabilityTtlSeconds(),
                    List.of(new ReplayBatchItemDTO(
                            testCase.getSourceTaskId(),
                            EvaluationRunPersistenceService.requestKey(retryDraft.attempt().getId())
                    ))
            )));
            persistenceService.attachDispatch(draft, response, segmentService.nextBatchNo(run.getId()));
        } catch (RuntimeException exception) {
            persistenceService.pauseDispatch(draft);
        }

        return detail(run.getId());
    }

    // -------------------------- 私有工具方法 --------------------------

    /**
     * 根据Attempt，在分片内批量查询并筛选当前任务的证据包
     */
    private EvaluationEvidenceBundleVO evidenceForAttempt(EvaluationRunEntity run, EvaluationCaseAttemptEntity target) {
        List<EvaluationCaseAttemptEntity> segmentAttempts = attemptMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseAttemptEntity>()
                        .eq(EvaluationCaseAttemptEntity::getCapabilitySegmentId, target.getCapabilitySegmentId())
        );
        List<Long> taskIds = segmentAttempts.stream()
                .map(EvaluationCaseAttemptEntity::getReplayTaskId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        String capability = segmentService.requireActiveCapability(
                target.getCapabilitySegmentId(), run.getId(), run.getSpaceId()
        );
        return requireData(taskFeign.queryEvaluationEvidence(capability,
                new EvaluationTaskBatchQueryDTO(run.getId(), run.getSpaceId(), taskIds)))
                .stream()
                .filter(v -> target.getReplayTaskId().equals(v.taskId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "Replay 评估事实不可用"));
    }

    /**
     * 获取文档变更证据（document-change-validator专用）
     */
    private List<EvaluationDocumentChangeEvidenceVO> documentChangesForAttempt(
            EvaluationRunEntity run, EvaluationCaseAttemptEntity target) {
        List<EvaluationCaseAttemptEntity> segmentAttempts = attemptMapper.selectList(
                new LambdaQueryWrapper<EvaluationCaseAttemptEntity>()
                        .eq(EvaluationCaseAttemptEntity::getCapabilitySegmentId, target.getCapabilitySegmentId())
        );
        List<Long> taskIds = segmentAttempts.stream()
                .map(EvaluationCaseAttemptEntity::getReplayTaskId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        String capability = segmentService.requireActiveCapability(
                target.getCapabilitySegmentId(), run.getId(), run.getSpaceId()
        );
        return requireData(taskFeign.queryEvaluationDocumentChanges(capability,
                new EvaluationTaskBatchQueryDTO(run.getId(), run.getSpaceId(), taskIds)))
                .stream()
                .filter(v -> target.getReplayTaskId().equals(v.taskId()))
                .toList();
    }

    /**
     * 续期WorkerCapability，并把新分片绑定到Attempt
     */
    private void renewWorkerCapability(EvaluationRunEntity run, EvaluationCaseAttemptEntity attempt, Long ttlSeconds) {
        List<Long> taskIds = List.of(attempt.getReplayTaskId());
        EvaluationWorkerCapabilityVO response = requireData(taskFeign.renewEvaluationWorkerCapability(
                new EvaluationWorkerCapabilityRenewDTO(run.getId(), run.getSpaceId(), ttlSeconds, taskIds)
        ));

        String expectedHash = StableSnapshotUtils.snapshotHash(1, taskIds);
        if (!run.getId().equals(response.runId())
                || !run.getSpaceId().equals(response.spaceId())
                || !expectedHash.equals(response.taskIdsHash())) {
            throw new BusinessException(ErrorCode.CONFLICT, "WorkerCapability 续签响应身份不匹配");
        }

        var segment = segmentService.append(
                run.getId(),
                run.getSpaceId(),
                segmentService.nextBatchNo(run.getId()),
                response.taskIdsHash(),
                response.workerCapability(),
                response.expiresAt()
        );
        attempt.setCapabilitySegmentId(segment.getId());
        attemptMapper.updateById(attempt);
    }

    /**
     * 解析创建请求的用例列表，支持单用例 / 数据集版本批量用例两种模式
     */
    private List<EvaluationTestCaseVersionEntity> resolveCaseVersions(EvaluationRunCreateDTO request) {
        if (request.singleTestCaseVersionId() != null) {
            EvaluationTestCaseVersionEntity version = testCaseVersionMapper.selectById(request.singleTestCaseVersionId());
            requirePublishedCase(version, request.spaceId());
            return List.of(version);
        }

        EvaluationDatasetVersionEntity dataset = datasetVersionMapper.selectById(request.datasetVersionId());
        EvaluationDatasetEntity datasetResource = dataset == null ? null : datasetMapper.selectById(dataset.getDatasetId());

        if (dataset == null
                || !request.spaceId().equals(dataset.getSpaceId())
                || !EvaluationVersionStatus.PUBLISHED.name().equals(dataset.getStatus())
                || datasetResource == null
                || Boolean.TRUE.equals(datasetResource.getArchived())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能运行同空间已发布 DatasetVersion");
        }

        List<EvaluationDatasetCaseEntity> bindings = datasetCaseMapper.selectList(
                new LambdaQueryWrapper<EvaluationDatasetCaseEntity>()
                        .eq(EvaluationDatasetCaseEntity::getDatasetVersionId, dataset.getId())
                        .eq(EvaluationDatasetCaseEntity::getEnabled, true)
                        .orderByAsc(EvaluationDatasetCaseEntity::getSortOrder, EvaluationDatasetCaseEntity::getId)
        );

        if (bindings.isEmpty() || bindings.size() > EvaluationConstant.MAX_DATASET_CASE_COUNT) {
            throw new BusinessException(ErrorCode.CONFLICT, "DatasetVersion 启用用例数量无效");
        }

        Map<Long, EvaluationTestCaseVersionEntity> versions = testCaseVersionMapper.selectBatchIds(
                        bindings.stream().map(EvaluationDatasetCaseEntity::getTestCaseVersionId).toList())
                .stream()
                .collect(Collectors.toMap(EvaluationTestCaseVersionEntity::getId, Function.identity()));

        return bindings.stream()
                .map(binding -> {
                    EvaluationTestCaseVersionEntity v = versions.get(binding.getTestCaseVersionId());
                    requirePublishedCase(v, request.spaceId());
                    return v;
                })
                .toList();
    }

    /**
     * 校验TestCaseVersion存在、同空间、已发布、用例本体未归档
     */
    private void requirePublishedCase(EvaluationTestCaseVersionEntity version, Long spaceId) {
        EvaluationTestCaseEntity testCase = version == null ? null : testCaseMapper.selectById(version.getTestCaseId());
        if (version == null
                || !spaceId.equals(version.getSpaceId())
                || !EvaluationVersionStatus.PUBLISHED.name().equals(version.getStatus())
                || testCase == null
                || Boolean.TRUE.equals(testCase.getArchived())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能运行同空间已发布 TestCaseVersion");
        }
    }

    /**
     * 创建参数互斥校验：datasetVersionId、singleTestCaseVersionId二选一必填
     */
    private void requireOneTarget(EvaluationRunCreateDTO request) {
        boolean datasetPresent = request.datasetVersionId() != null;
        boolean singleCasePresent = request.singleTestCaseVersionId() != null;
        if (datasetPresent == singleCasePresent) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "DatasetVersion 与单 TestCaseVersion 必须且只能指定一个");
        }
    }

    /**
     * RunDraft → VO 转换
     */
    private EvaluationRunVO toVO(EvaluationRunPersistenceService.RunDraft draft) {
        List<EvaluationCaseRunVO> caseVos = draft.cases().stream()
                .map(item -> EvaluationCaseRunVO.from(item.caseRun(), item.attempt()))
                .toList();
        return EvaluationRunVO.from(draft.run(), caseVos);
    }

    /**
     * 从map取出当前Attempt，不存在抛异常
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
     * Feign统一解包
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(
                    result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "task-service 调用失败" : result.message()
            );
        }
        return result.data();
    }

    /**
     * 根据ID加载Run，不存在抛NOT_FOUND
     */
    private EvaluationRunEntity requireRun(Long id) {
        EvaluationRunEntity run = runMapper.selectById(id);
        if (run == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "EvaluationRun 不存在");
        }
        return run;
    }

    /**
     * 判断Run顶层是否为终态
     */
    private boolean terminalRun(String status) {
        return EvaluationRunStatus.COMPLETED.name().equals(status)
                || EvaluationRunStatus.COMPLETED_WITH_ERRORS.name().equals(status)
                || EvaluationRunStatus.CANCELED.name().equals(status)
                || EvaluationRunStatus.FAILED.name().equals(status);
    }
}
