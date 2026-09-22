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
 * 负责评估任务Run、CaseRun、CaseAttempt的创建、分发绑定、暂停、重试新建，所有数据变更都在独立短事务内执行。
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
     * 事务内批量生成Run、每个用例对应的CaseRun和Attempt，状态初始为CREATED，返回草稿对象用于后续分发绑定
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
            caseRun.setCurrentAttemptId(attempt.getId());
            caseRunMapper.insert(caseRun);
            attemptMapper.insert(attempt);
            cases.add(new RunCaseDraft(caseRun, attempt));
        }
        return new RunDraft(run, List.copyOf(cases));
    }

    /**
     * 绑定分发结果，默认batchNo=1
     */
    @Transactional
    public RunDraft attachDispatch(RunDraft draft, ReplayBatchCreateVO response) {
        return attachDispatch(draft, response, 1);
    }

    /**
     * 将Replay批量分发结果绑定到当前RunDraft
     * 校验run/space身份、用例数量一致性；创建worker能力分片，更新Attempt、CaseRun状态为REPLAY_CREATED，Run状态切为RUNNING并记录启动时间
     */
    @Transactional
    public RunDraft attachDispatch(RunDraft draft, ReplayBatchCreateVO response, int batchNo) {
        if (!draft.run().getId().equals(response.runId())
                || !draft.run().getSpaceId().equals(response.spaceId())
                || response.items().size() != draft.cases().size()) {
            throw new BusinessException(ErrorCode.CONFLICT, "批量 Replay 响应身份不匹配");
        }
        Map<String, ReplayBatchItemVO> byRequestKey = response.items().stream()
                .collect(Collectors.toMap(ReplayBatchItemVO::derivationRequestKey, Function.identity()));
        for (RunCaseDraft item : draft.cases()) {
            ReplayBatchItemVO mapping = byRequestKey.get(requestKey(item.attempt().getId()));
            if (mapping == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "批量 Replay 响应缺少 Attempt 映射");
            }
        }
        var segment = segmentService.append(draft.run().getId(), draft.run().getSpaceId(), batchNo,
                response.taskIdsHash(), response.workerCapability(), response.workerCapabilityExpiresAt());
        for (RunCaseDraft item : draft.cases()) {
            String requestKey = requestKey(item.attempt().getId());
            ReplayBatchItemVO mapping = byRequestKey.get(requestKey);
            item.attempt().setReplayTaskId(mapping.replayTaskId());
            item.attempt().setCapabilitySegmentId(segment.getId());
            item.attempt().setStatus(EvaluationAttemptStatus.REPLAY_CREATED.name());
            item.attempt().setStartedAt(LocalDateTime.now());
            attemptMapper.updateById(item.attempt());
            item.caseRun().setStatus(EvaluationAttemptStatus.REPLAY_CREATED.name());
            caseRunMapper.updateById(item.caseRun());
        }
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
     * 暂停分发阶段的Run，状态置为PAUSED并记录暂停原因为分发资源不可用
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
     */
    public static String requestKey(Long attemptId) {
        return "evaluation-case-attempt:" + attemptId;
    }

    /**
     * 为单个用例创建重试Attempt
     * 新建Attempt，attemptNo自增；更新CaseRun当前AttemptId与状态；将Run切回DISPATCHING，等待重新分发
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
        caseRun.setCurrentAttemptId(attempt.getId());
        caseRun.setStatus(EvaluationAttemptStatus.CREATED.name());
        caseRun.setUpdatedAt(LocalDateTime.now());
        caseRunMapper.updateById(caseRun);
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
     * 单条用例草稿：包含CaseRun与当前Attempt
     */
    public record RunCaseDraft(EvaluationCaseRunEntity caseRun, EvaluationCaseAttemptEntity attempt) { }

    /**
     * 评估任务草稿：包含Run主记录 + 全部用例草稿列表
     */
    public record RunDraft(EvaluationRunEntity run, List<RunCaseDraft> cases) { }
}
