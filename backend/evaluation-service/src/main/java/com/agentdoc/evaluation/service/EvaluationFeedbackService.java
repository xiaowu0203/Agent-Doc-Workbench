package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.ChangeRequestFeedbackSnapshotVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.enums.EvaluationEvidenceType;
import com.agentdoc.evaluation.enums.EvaluationFeedbackLabel;
import com.agentdoc.evaluation.enums.EvaluationFeedbackSourceType;
import com.agentdoc.evaluation.mapper.EvaluationCaseAttemptMapper;
import com.agentdoc.evaluation.mapper.EvaluationCaseRunMapper;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.vo.EvaluationFeedbackVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;

/**
 * 评估反馈服务
 * 处理人工反馈录入、ChangeRequest反馈快照导入；
 * 统一解析反馈目标归属，校验空间权限与目标身份一致性，生成反馈记录。
 */
@Service
@RequiredArgsConstructor
public class EvaluationFeedbackService {
    private final EvaluationFeedbackMapper feedbackMapper;
    private final EvaluationCaseRunMapper caseRunMapper;
    private final EvaluationCaseAttemptMapper attemptMapper;
    private final EvaluationEvidenceReferenceMapper evidenceMapper;
    private final EvaluationResultMapper resultMapper;
    private final SpaceAccessService spaceAccessService;
    private final TaskFeign taskFeign;

    /**
     * 创建人工评估反馈
     * 校验空间权限，解析反馈目标，生成反馈实体并写入数据库，返回VO
     */
    @Transactional
    public EvaluationFeedbackVO create(EvaluationFeedbackCreateDTO request) {
        spaceAccessService.requirePermission(request.spaceId(), EVALUATION_RUN);
        FeedbackTarget target = resolveTarget(request);
        EvaluationFeedbackEntity entity = new EvaluationFeedbackEntity();
        entity.setId(IdWorker.getId());
        entity.setSpaceId(request.spaceId());
        entity.setRunId(target == null ? null : target.runId());
        entity.setCaseRunId(target == null ? null : target.caseRunId());
        entity.setTaskId(target.taskId());
        entity.setExecutionId(target.executionId());
        entity.setSourceType(EvaluationFeedbackSourceType.MANUAL.name());
        entity.setSourceBusinessId(String.valueOf(entity.getId()));
        entity.setLabel(request.label().name());
        entity.setScore(request.score());
        entity.setComment(trim(request.comment()));
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        entity.setSourceHash(StableSnapshotUtils.snapshotHash(1, Map.of(
                "feedbackId", entity.getId(), "label", entity.getLabel(),
                "score", value(entity.getScore()), "comment", value(entity.getComment()))));
        feedbackMapper.insert(entity);
        return EvaluationFeedbackVO.from(entity);
    }

    /**
     * 导入ChangeRequest反馈快照
     * 远程拉取变更请求快照，幂等写入反馈记录；
     * 存在相同sourceHash记录则直接返回，避免重复导入
     */
    @Transactional
    public EvaluationFeedbackVO importChangeRequest(Long changeRequestId) {
        ChangeRequestFeedbackSnapshotVO snapshot = requireData(
                taskFeign.getChangeRequestFeedbackSnapshot(changeRequestId));
        spaceAccessService.requirePermission(snapshot.spaceId(), EVALUATION_RUN);
        EvaluationFeedbackEntity existing = feedbackMapper.selectOne(
                new LambdaQueryWrapper<EvaluationFeedbackEntity>()
                        .eq(EvaluationFeedbackEntity::getSourceType,
                                EvaluationFeedbackSourceType.CHANGE_REQUEST.name())
                        .eq(EvaluationFeedbackEntity::getSourceBusinessId, String.valueOf(changeRequestId))
                        .eq(EvaluationFeedbackEntity::getSourceHash, snapshot.sourceHash())
                        .last("LIMIT 1"));
        if (existing != null) {
            return EvaluationFeedbackVO.from(existing);
        }
        FeedbackTarget target = resolveTaskTarget(snapshot.spaceId(), snapshot.taskId(), snapshot.executionId(), false);
        EvaluationFeedbackEntity entity = new EvaluationFeedbackEntity();
        entity.setId(IdWorker.getId());
        entity.setSpaceId(snapshot.spaceId());
        entity.setRunId(target == null ? null : target.runId());
        entity.setCaseRunId(target == null ? null : target.caseRunId());
        entity.setTaskId(snapshot.taskId());
        entity.setExecutionId(snapshot.executionId());
        entity.setSourceType(EvaluationFeedbackSourceType.CHANGE_REQUEST.name());
        entity.setSourceBusinessId(String.valueOf(changeRequestId));
        entity.setSourceHash(snapshot.sourceHash());
        entity.setLabel(label(snapshot.status()).name());
        entity.setFactsJson(JsonUtils.toJson(Map.of(
                "status", snapshot.status(),
                "resolutionType", value(snapshot.resolutionType()),
                "reviewCommentHash", value(snapshot.reviewCommentHash()),
                "reviewedBy", value(snapshot.reviewedBy()),
                "reviewedAt", snapshot.reviewedAt() == null ? "" : snapshot.reviewedAt().toString(),
                "revisionNo", value(snapshot.revisionNo()))));
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        feedbackMapper.insert(entity);
        return EvaluationFeedbackVO.from(entity);
    }

    /**
     * 解析反馈目标
     * 支持caseRunId / taskId / executionId多入口，校验目标身份一致性，合并目标信息
     */
    private FeedbackTarget resolveTarget(EvaluationFeedbackCreateDTO request) {
        if (request.caseRunId() == null && request.taskId() == null && request.executionId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "caseRunId、taskId、executionId 至少指定一个");
        }
        FeedbackTarget target = request.caseRunId() == null ? null
                : resolveCaseTarget(request.spaceId(), request.caseRunId());
        FeedbackTarget taskTarget = resolveTaskTarget(request.spaceId(), request.taskId(), request.executionId(), true);
        if (target == null) {
            return taskTarget;
        }
        if (taskTarget != null && !Objects.equals(target.caseRunId(), taskTarget.caseRunId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "人工反馈目标身份不一致");
        }
        if (request.taskId() != null && !Objects.equals(request.taskId(), target.taskId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "人工反馈目标身份不一致");
        }
        return new FeedbackTarget(target.runId(), target.caseRunId(), target.taskId(),
                request.executionId() == null ? target.executionId() : request.executionId());
    }

    /**
     * 通过caseRunId解析反馈目标
     * 查询CaseRun与当前Attempt，校验空间归属，组装FeedbackTarget
     */
    private FeedbackTarget resolveCaseTarget(Long spaceId, Long caseRunId) {
        EvaluationCaseRunEntity caseRun = caseRunMapper.selectById(caseRunId);
        EvaluationCaseAttemptEntity attempt = caseRun == null ? null
                : attemptMapper.selectById(caseRun.getCurrentAttemptId());
        if (caseRun == null || attempt == null || !spaceId.equals(caseRun.getSpaceId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "反馈 CaseRun 不存在");
        }
        return new FeedbackTarget(caseRun.getRunId(), caseRun.getId(), attempt.getReplayTaskId(), null);
    }

    /**
     * 通过taskId或executionId解析反馈目标
     * 优先按taskId查询Attempt；无taskId则通过executionId关联证据、评估结果反向查找Attempt
     * required标记控制是否在找不到目标时抛异常
     */
    private FeedbackTarget resolveTaskTarget(Long spaceId, Long taskId, Long executionId, boolean required) {
        EvaluationCaseAttemptEntity attempt = null;
        if (taskId != null) {
            attempt = attemptMapper.selectOne(new LambdaQueryWrapper<EvaluationCaseAttemptEntity>()
                    .eq(EvaluationCaseAttemptEntity::getSpaceId, spaceId)
                    .eq(EvaluationCaseAttemptEntity::getReplayTaskId, taskId)
                    .orderByDesc(EvaluationCaseAttemptEntity::getId).last("LIMIT 1"));
        }
        if (attempt == null && executionId != null) {
            EvaluationEvidenceReferenceEntity evidence = evidenceMapper.selectOne(
                    new LambdaQueryWrapper<EvaluationEvidenceReferenceEntity>()
                            .eq(EvaluationEvidenceReferenceEntity::getSpaceId, spaceId)
                            .eq(EvaluationEvidenceReferenceEntity::getEvidenceType,
                                    EvaluationEvidenceType.AGENT_EXECUTION.name())
                            .eq(EvaluationEvidenceReferenceEntity::getBusinessId, String.valueOf(executionId))
                            .orderByDesc(EvaluationEvidenceReferenceEntity::getId).last("LIMIT 1"));
            EvaluationResultEntity result = evidence == null ? null : resultMapper.selectById(evidence.getResultId());
            attempt = result == null ? null : attemptMapper.selectById(result.getCaseAttemptId());
        }
        if (attempt == null) {
            if (required && (taskId != null || executionId != null)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "反馈 Task/Execution 未关联 Evaluation Attempt");
            }
            return null;
        }
        EvaluationCaseRunEntity caseRun = caseRunMapper.selectById(attempt.getCaseRunId());
        if (caseRun == null || !spaceId.equals(caseRun.getSpaceId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "反馈目标归属不一致");
        }
        return new FeedbackTarget(attempt.getRunId(), caseRun.getId(), attempt.getReplayTaskId(), executionId);
    }

    /**
     * 将ChangeRequest状态映射为评估反馈标签枚举
     */
    private EvaluationFeedbackLabel label(String status) {
        return switch (status) {
            case "APPROVED", "MERGED" -> EvaluationFeedbackLabel.ACCEPTED;
            case "REJECTED" -> EvaluationFeedbackLabel.REJECTED;
            case "RETURNED" -> EvaluationFeedbackLabel.NEEDS_CHANGES;
            default -> throw new BusinessException(ErrorCode.CONFLICT, "ChangeRequest 状态不可导入");
        };
    }

    /**
     * 校验Feign返回Result，非成功状态抛业务异常
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "读取 ChangeRequest 反馈快照失败" : result.message());
        }
        return result.data();
    }

    /**
     * 字符串裁剪工具，空白字符串转为null
     */
    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 空值兜底工具，null替换为空字符串，用于快照hash计算
     */
    private Object value(Object value) {
        return value == null ? "" : value;
    }

    /**
     * 反馈目标载体，承载run、caseRun、task、execution关联ID
     */
    private record FeedbackTarget(Long runId, Long caseRunId, Long taskId, Long executionId) {
    }
}
