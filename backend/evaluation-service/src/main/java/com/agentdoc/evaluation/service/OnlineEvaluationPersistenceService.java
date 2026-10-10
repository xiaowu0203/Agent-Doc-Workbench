package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluationOutcome;
import com.agentdoc.evaluation.evaluator.OnlineOriginalTextEvaluator;
import com.agentdoc.evaluation.evaluator.OnlineRuleContractValidator;
import com.agentdoc.evaluation.mapper.EvaluationEvidenceReferenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricMapper;
import com.agentdoc.evaluation.mapper.EvaluationMetricEvidenceMapper;
import com.agentdoc.evaluation.mapper.EvaluationResultMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineEvaluationAttemptMapper;
import com.agentdoc.evaluation.metric.EvaluationSubjectValidator;
import com.agentdoc.evaluation.metric.MetricContractValidator;
import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEvidenceEntity;
import com.agentdoc.evaluation.pojo.entity.EvaluationResultEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineEvaluationAttemptEntity;
import com.agentdoc.evaluation.pojo.vo.OnlineEvaluationVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Objects;
import static com.agentdoc.evaluation.constant.EvaluationConstant.METRIC_CONTRACT_VERSION;
import static com.agentdoc.common.enums.OnlineReasonCode.ONLINE_EVALUATION_IDENTITY_CONFLICT;

/** 仅持有本领域 Mapper；追加结果与全部引用原子落库，不在事务内 RPC。 */
@Service
@RequiredArgsConstructor
public class OnlineEvaluationPersistenceService {
    private static final String SUBJECT = "ONLINE_TASK";
    private final OnlineAssignmentMapper assignments;
    private final OnlineEvaluationAttemptMapper attempts;
    private final EvaluationResultMapper results;
    private final EvaluationEvidenceReferenceMapper evidence;
    private final EvaluationMetricMapper metrics;
    private final EvaluationMetricEvidenceMapper links;

    public OnlineEvaluationAttemptEntity prior(Long assignmentId, Long versionId, String key) {
        return attempts.selectOne(new LambdaQueryWrapper<OnlineEvaluationAttemptEntity>()
                .eq(OnlineEvaluationAttemptEntity::getAssignmentId, assignmentId)
                .eq(OnlineEvaluationAttemptEntity::getEvaluatorVersionId, versionId)
                .eq(OnlineEvaluationAttemptEntity::getClientRequestKey, key));
    }

    public OnlineEvaluationVO reuse(OnlineEvaluationAttemptEntity attempt, String hash) {
        if (!Objects.equals(hash, attempt.getRequestHash())) { throw conflict(); }
        var result = results.selectById(attempt.getResultId());
        if (result == null || !SUBJECT.equals(result.getSubjectType()) || !Objects.equals(result.getOnlineEvaluationAttemptId(), attempt.getId())
                || !Objects.equals(result.getOnlineAssignmentId(), attempt.getAssignmentId()) || !Objects.equals(result.getEvaluatorVersionId(), attempt.getEvaluatorVersionId())) {
            throw conflict();
        }
        return view(attempt, result);
    }

    @Transactional(rollbackFor = Exception.class)
    public OnlineEvaluationVO append(OnlineAssignmentEntity origin, Long versionId, String ruleKey, String key,
            String requestHash, String expectedHash, Long actor, LocalDateTime startedAt, DeterministicEvaluationOutcome outcome) {
        var assignment = assignments.lockTask(origin.getTaskId());
        if (assignment == null || !Objects.equals(assignment.getId(), origin.getId())
                || !Objects.equals(assignment.getBindingHash(), origin.getBindingHash()) || !Objects.equals(assignment.getExecutionId(), origin.getExecutionId())
                || !Integer.valueOf(2).equals(assignment.getBindingSchemaVersion()) || assignment.getExecutionId() == null) { throw conflict(); }
        var existing = prior(assignment.getId(), versionId, key);
        if (existing != null) { return reuse(existing, requestHash); }
        var attempt = new OnlineEvaluationAttemptEntity();
        attempt.setId(IdWorker.getId()); attempt.setAssignmentId(assignment.getId()); attempt.setSpaceId(assignment.getSpaceId());
        attempt.setEvaluatorVersionId(versionId); attempt.setAttemptNo(Math.addExact(attempts.lastAttempt(assignment.getId()), 1));
        attempt.setRuleKey(ruleKey); attempt.setClientRequestKey(key); attempt.setRequestHash(requestHash); attempt.setExpectedHash(expectedHash);
        attempt.setCreatedBy(actor); attempt.setStartedAt(startedAt); attempt.setFinishedAt(LocalDateTime.now());
        attempt.setStatus("COMPLETED"); attempt.setReasonCode(outcome.summaryCode());
        var result = new EvaluationResultEntity(); result.setId(IdWorker.getId());
        result.setSubjectType(SUBJECT); result.setOnlineAssignmentId(assignment.getId()); result.setOnlineEvaluationAttemptId(attempt.getId());
        result.setTaskId(assignment.getTaskId()); result.setExecutionId(assignment.getExecutionId()); result.setSpaceId(assignment.getSpaceId());
        result.setEvaluatorVersionId(versionId); result.setEvaluationAttemptNo(attempt.getAttemptNo()); result.setStatus(outcome.status().name());
        result.setSummaryCode(outcome.summaryCode()); result.setDetailsJson(outcome.detailsJson()); result.setImplementationVersion(OnlineOriginalTextEvaluator.IMPLEMENTATION_VERSION);
        result.setStartedAt(startedAt); result.setFinishedAt(attempt.getFinishedAt()); attempt.setResultId(result.getId());
        EvaluationSubjectValidator.validate(result); attempts.insert(attempt); results.insert(result);
        var referenceIds = new HashMap<String, Long>();
        for (var ref : outcome.evidence()) {
            var row = new EvaluationEvidenceReferenceEntity(); row.setId(IdWorker.getId()); row.setSubjectType(SUBJECT);
            row.setOnlineAssignmentId(assignment.getId()); row.setOnlineEvaluationAttemptId(attempt.getId()); row.setTaskId(assignment.getTaskId());
            row.setExecutionId(assignment.getExecutionId()); row.setSpaceId(assignment.getSpaceId()); row.setResultId(result.getId());
            row.setEvidenceType(ref.evidenceType().name()); row.setBusinessId(ref.businessId()); row.setContentHash(ref.contentHash());
            row.setSummary(ref.summary()); row.setLocatorJson(ref.locatorJson()); EvaluationSubjectValidator.validate(row);
            if (referenceIds.put(ref.referenceKey(), row.getId()) != null) { throw conflict(); } evidence.insert(row);
        }
        for (var output : outcome.metrics()) {
            var value = output.value(); MetricContractValidator.validate(OnlineRuleContractValidator.TEXT, value);
            var row = new EvaluationMetricEntity(); row.setId(IdWorker.getId()); row.setSubjectType(SUBJECT);
            row.setOnlineAssignmentId(assignment.getId()); row.setOnlineEvaluationAttemptId(attempt.getId()); row.setTaskId(assignment.getTaskId());
            row.setExecutionId(assignment.getExecutionId()); row.setSpaceId(assignment.getSpaceId()); row.setEvaluationResultId(result.getId());
            row.setEvaluatorVersionId(versionId); row.setContractVersion(METRIC_CONTRACT_VERSION); row.setSource(value.source().name()); row.setProducerId(result.getId());
            row.setMetricKey(value.metricKey()); row.setValueType(value.valueType().name()); row.setNumericValue(value.numericValue());
            row.setBooleanValue(value.booleanValue()); row.setStringValue(value.stringValue()); row.setUnit(value.unit()); row.setDirection(value.direction().name());
            EvaluationSubjectValidator.validate(row); metrics.insert(row);
            for (var reference : output.evidenceReferenceKeys()) {
                var evidenceId = referenceIds.get(reference); if (evidenceId == null) { throw conflict(); }
                var link = new EvaluationMetricEvidenceEntity(); link.setMetricId(row.getId()); link.setEvidenceReferenceId(evidenceId); links.insert(link);
            }
        }
        return view(attempt, result);
    }

    private static OnlineEvaluationVO view(OnlineEvaluationAttemptEntity attempt, EvaluationResultEntity result) {
        return new OnlineEvaluationVO(attempt.getId().toString(), attempt.getAssignmentId().toString(), attempt.getRuleKey(),
                attempt.getEvaluatorVersionId().toString(), attempt.getAttemptNo(), result.getId().toString(), result.getStatus(), result.getSummaryCode());
    }
    private static BusinessException conflict() { return new BusinessException(ErrorCode.CONFLICT, ONLINE_EVALUATION_IDENTITY_CONFLICT.name()); }
}
