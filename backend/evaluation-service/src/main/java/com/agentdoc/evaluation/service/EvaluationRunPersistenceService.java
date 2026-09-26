package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.ReplayBatchCreateVO;
import com.agentdoc.common.feign.vo.ReplayBatchItemVO;
import com.agentdoc.evaluation.enums.EvaluationAttemptStatus;
import com.agentdoc.evaluation.enums.EvaluationPauseReason;
import com.agentdoc.evaluation.enums.EvaluationRunStatus;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationRunMapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationRunEntity;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * EvaluationRun 的短事务写入边界。
 * <p>负责评估任务 Run、CaseRun、CaseAttempt 的创建、分发绑定、暂停、重试新建；
 * 所有数据变更都封装在独立短事务内，避免长事务锁持有。
 * 职责：只做持久化变更与状态流转，不含调度、worker 选择、实际执行逻辑；
 * 产出 Draft 内存对象，供上层调度链路继续传递使用。
 */
@Service
@RequiredArgsConstructor
public class EvaluationRunPersistenceService {

    private final EvaluationRunMapper runMapper;
    private final EvaluationCaseRunMapper caseRunMapper;
    private final EvaluationCaseAttemptMapper attemptMapper;
    private final WorkerCapabilitySegmentService segmentService;

    /**
     * 创建评估任务Run以及配套的CaseRun与首轮CaseAttempt
     * <p>事务内一次性生成：Run主记录 + 每一个用例对应的CaseRun + 首轮Attempt；
     * 初始状态全部置为 CREATED，返回 RunDraft 草稿对象，供后续分发绑定流程使用。
     * </p>
     * @param spaceId 空间ID
     * @param datasetVersionId 数据集版本ID
     * @param singleTestCaseVersionId 单用例模式下的用例版本ID，批量模式可为null
     * @param createdBy 创建人ID
     * @param testCaseVersionIds 本次评估包含的全部用例版本ID列表
     * @return RunDraft 包含Run和所有用例CaseRun、首轮Attempt内存草稿
     */
    @Transactional
    public RunDraft create(Long spaceId, Long datasetVersionId, Long singleTestCaseVersionId,
                           Long createdBy, List<Long> testCaseVersionIds) {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(IdWorker.getId());
        run.setSpaceId(spaceId);
        run.setDatasetVersionId(datasetVersionId);
        run.setSingleTestCaseVersionId(singleTestCaseVersionId);
        run.setStatus(EvaluationRunStatus.DISPATCHING.name());
        run.setCancelRequested(false);
        run.setCaseCount(testCaseVersionIds.size());
        run.setReconciliationFailureCount(0);
        run.setCreatedBy(createdBy);
        runMapper.insert(run);

        List<RunCaseDraft> cases = new ArrayList<>(testCaseVersionIds.size());
        for (Long testCaseVersionId : testCaseVersionIds) {
            EvaluationCaseRunEntity caseRun = new EvaluationCaseRunEntity();
            caseRun.setId(IdWorker.getId());
            caseRun.setRunId(run.getId());
            caseRun.setSpaceId(spaceId);
            caseRun.setTestCaseVersionId(testCaseVersionId);
            caseRun.setStatus(EvaluationAttemptStatus.CREATED.name());

            EvaluationCaseAttemptEntity attempt = new EvaluationCaseAttemptEntity();
            attempt.setId(IdWorker.getId());
            attempt.setCaseRunId(caseRun.getId());
            attempt.setRunId(run.getId());
            attempt.setSpaceId(spaceId);
            attempt.setAttemptNo(1);
            attempt.setStatus(EvaluationAttemptStatus.CREATED.name());

            // CaseRun 指向当前最新Attempt
            caseRun.setCurrentAttemptId(attempt.getId());
            cases.add(new RunCaseDraft(caseRun, attempt));
        }
        if (!cases.isEmpty()) {
            caseRunMapper.insertBatch(cases.stream().map(RunCaseDraft::caseRun).toList());
            attemptMapper.insertBatch(cases.stream().map(RunCaseDraft::attempt).toList());
        }
        return new RunDraft(run, List.copyOf(cases));
    }

    /**
     * 绑定分发结果，默认批次号 batchNo=1
     * @param draft 创建阶段返回的Run草稿
     * @param response 回放批量创建响应
     * @return 更新后RunDraft
     */
    @Transactional
    public RunDraft attachDispatch(RunDraft draft, ReplayBatchCreateVO response) {
        return attachDispatch(draft, response, 1);
    }

    /**
     * 将Replay批量分发结果绑定到当前RunDraft
     * <p>执行逻辑：
     * 1. 校验Run、Space身份以及用例数量完全匹配，防止跨任务绑定；
     * 2. 校验所有Attempt都能在批量响应中找到映射；
     * 3. 创建Worker能力分片记录，绑定worker能力与过期时间；
     * 4. 更新每一条Attempt：同步写入兼容 Replay Task ID 与通用执行 Task ID、分片ID、状态REPLAY_CREATED、启动时间；
     * 5. 更新CaseRun状态为REPLAY_CREATED；
     * 6. Run状态切换为RUNNING，清空暂停原因、重置对账失败计数、记录Run启动时间。
     * </p>
     * @param draft Run草稿对象
     * @param response Replay批量创建返回结果
     * @param batchNo 当前分发批次号，支持多批次分发
     * @return 更新完成后的RunDraft（内存对象已同步新状态）
     */
    @Transactional
    public RunDraft attachDispatch(RunDraft draft, ReplayBatchCreateVO response, int batchNo) {
        if (!draft.run().getId().equals(response.runId())
                || !draft.run().getSpaceId().equals(response.spaceId())
                || response.items().size() != draft.cases().size()) {
            throw new BusinessException(ErrorCode.CONFLICT, "批量 Replay 响应身份不匹配");
        }

        // 按派生请求key建立映射表，方便快速查找
        Map<String, ReplayBatchItemVO> byRequestKey = response.items().stream()
                .collect(Collectors.toMap(ReplayBatchItemVO::derivationRequestKey, Function.identity()));

        // 校验：每个Attempt都必须存在对应的分发映射
        for (RunCaseDraft item : draft.cases()) {
            ReplayBatchItemVO mapping = byRequestKey.get(requestKey(item.attempt().getId()));
            if (mapping == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "批量 Replay 响应缺少 Attempt 映射");
            }
        }

        // 创建Worker能力分片，用于后续路由与过期校验
        var segment = segmentService.append(draft.run().getId(), draft.run().getSpaceId(), batchNo,
                response.taskIdsHash(), response.workerCapability(), response.workerCapabilityExpiresAt());

        // 更新Attempt与CaseRun状态、回放任务ID、分片ID、启动时间
        LocalDateTime startedAt = LocalDateTime.now();
        for (RunCaseDraft item : draft.cases()) {
            String requestKey = requestKey(item.attempt().getId());
            ReplayBatchItemVO mapping = byRequestKey.get(requestKey);

            item.attempt().setReplayTaskId(mapping.replayTaskId());
            item.attempt().setExecutionTaskId(mapping.replayTaskId());
            item.attempt().setCapabilitySegmentId(segment.getId());
            item.attempt().setStatus(EvaluationAttemptStatus.REPLAY_CREATED.name());
            item.attempt().setStartedAt(startedAt);

            item.caseRun().setStatus(EvaluationAttemptStatus.REPLAY_CREATED.name());
        }
        attemptMapper.updateBatch(draft.cases().stream().map(RunCaseDraft::attempt).toList());
        caseRunMapper.updateBatch(draft.cases().stream().map(RunCaseDraft::caseRun).toList());

        // Run切换为运行态，清除暂停标记、重置失败计数、记录启动时间
        draft.run().setStatus(EvaluationRunStatus.RUNNING.name());
        draft.run().setPauseReason(null);
        draft.run().setReconciliationFailureCount(0);
        draft.run().setStartedAt(LocalDateTime.now());
        runMapper.updateById(draft.run());
        runMapper.update(null, new LambdaUpdateWrapper<EvaluationRunEntity>()
                .eq(EvaluationRunEntity::getId, draft.run().getId())
                .set(EvaluationRunEntity::getPauseReason, null));

        return draft;
    }

    /**
     * 暂停处于分发阶段的Run
     * <p>状态置为PAUSED，暂停原因为 DISPATCH_UNAVAILABLE（分发资源不可用）。
     * 仅修改Run主记录，不改动CaseRun/Attempt；重试恢复时由上层重新走分发链路。
     * </p>
     * @param draft Run草稿对象
     * @return 更新后RunDraft
     */
    @Transactional
    public RunDraft pauseDispatch(RunDraft draft) {
        draft.run().setStatus(EvaluationRunStatus.PAUSED.name());
        draft.run().setPauseReason(EvaluationPauseReason.DISPATCH_UNAVAILABLE.name());
        runMapper.updateById(draft.run());
        return draft;
    }

    /**
     * 生成Attempt对应的请求唯一key，用于和replay批量任务做映射
     * <p>格式固定前缀 + attemptId，作为调度层两端匹配的唯一标识。
     * </p>
     * @param attemptId 用例尝试ID
     * @return 映射用请求key
     */
    public static String requestKey(Long attemptId) {
        return "evaluation-case-attempt:" + attemptId;
    }

    /**
     * 为单个用例创建重试Attempt
     * <p>流程：
     * 1. 新建Attempt，attemptNo = 上一次attemptNo + 1；
     * 2. 更新CaseRun的currentAttemptId指向新Attempt，状态重置为CREATED；
     * 3. 将Run整体切回 DISPATCHING，清空暂停、清除完成时间、重置对账失败计数，等待重新分发。
     * </p>
     * @param run 评估任务主记录
     * @param caseRun 当前用例运行记录
     * @param previous 上一轮失败/异常的Attempt
     * @return RunCaseDraft：更新后的CaseRun + 新建的Attempt
     */
    @Transactional
    public RunCaseDraft createReplayRetry(EvaluationRunEntity run, EvaluationCaseRunEntity caseRun,
                                          EvaluationCaseAttemptEntity previous) {
        EvaluationCaseAttemptEntity attempt = new EvaluationCaseAttemptEntity();
        attempt.setId(IdWorker.getId());
        attempt.setCaseRunId(caseRun.getId());
        attempt.setRunId(run.getId());
        attempt.setSpaceId(run.getSpaceId());
        attempt.setAttemptNo(previous.getAttemptNo() + 1);
        attempt.setStatus(EvaluationAttemptStatus.CREATED.name());
        attemptMapper.insert(attempt);

        // CaseRun 切换到新的重试Attempt
        caseRun.setCurrentAttemptId(attempt.getId());
        caseRun.setStatus(EvaluationAttemptStatus.CREATED.name());
        caseRun.setUpdatedAt(LocalDateTime.now());
        caseRunMapper.updateById(caseRun);

        // Run回到待分发状态，等待重新调度
        run.setStatus(EvaluationRunStatus.DISPATCHING.name());
        run.setPauseReason(null);
        run.setReconciliationFailureCount(0);
        run.setFinishedAt(null);
        run.setUpdatedAt(LocalDateTime.now());
        runMapper.updateById(run);
        runMapper.update(null, new LambdaUpdateWrapper<EvaluationRunEntity>()
                .eq(EvaluationRunEntity::getId, run.getId())
                .set(EvaluationRunEntity::getPauseReason, null));

        return new RunCaseDraft(caseRun, attempt);
    }

    /**
     * 单条用例草稿：包含CaseRun与当前Attempt内存快照，用于调度链路传递
     * @param caseRun 用例运行记录
     * @param attempt 当前生效的用例尝试记录
     */
    public record RunCaseDraft(EvaluationCaseRunEntity caseRun, EvaluationCaseAttemptEntity attempt) { }

    /**
     * 评估任务草稿：Run主记录 + 全部用例RunCaseDraft列表
     * <p>属于内存对象，不持久化；用来在同一个调度流程内串联创建、分发、重试等连续操作，减少重复DB查询。
     * </p>
     * @param run 评估任务主记录内存快照
     * @param cases 所有用例草稿列表
     */
    public record RunDraft(EvaluationRunEntity run, List<RunCaseDraft> cases) { }
}
