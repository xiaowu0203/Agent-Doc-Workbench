package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.OnlineReviewGoalVO;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.mapper.OnlineBlindReviewReceiptMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.pojo.dto.OnlineQualityFeedbackDTO;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineBlindReviewReceiptEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

/** 资格证明与知情记录使用同一实验锁；客户端没有创建资格或指定actor的入口。 */
@Service
@RequiredArgsConstructor
public class OnlineBlindReviewReceiptService {
    private final OnlineExperimentMapper experiments;
    private final OnlineAssignmentMapper assignments;
    private final OnlineBlindReviewReceiptMapper receipts;
    private final OnlineReviewExposureService exposures;

    @Transactional(rollbackFor = Exception.class)
    public String capture(OnlineAssignmentEntity origin, Long actor, OnlineReviewGoalVO goal, String type, String evidenceId, String evidenceHash) {
        var experiment = experiments.lock(origin.getExperimentId()); var assignment = assignments.lockTask(origin.getTaskId());
        if (experiment == null || assignment == null || !Objects.equals(assignment.getId(), origin.getId()) || !Objects.equals(assignment.getExecutionId(), origin.getExecutionId())
                || !Objects.equals(assignment.getBindingHash(), origin.getBindingHash()) || !Objects.equals(goal.taskId(), assignment.getTaskId().toString())
                || !Objects.equals(goal.inputSnapshotHash(), assignment.getInputSnapshotHash())) { throw conflict(); }
        if (exposures.known(experiment.getId(), actor) != null || Objects.equals(actor, assignment.getCreatedBy()) || Objects.equals(actor, experiment.getCreatedBy())) { return "NON_BLINDED"; }
        if (!tracked(experiment)) { return "UNKNOWN"; }
        var existing = find(assignment.getId(), actor, type);
        if (existing != null) { verify(existing, assignment, type, evidenceId, evidenceHash, actor); return "BLINDED"; }
        var row = new OnlineBlindReviewReceiptEntity(); row.setId(IdWorker.getId()); row.setExperimentId(assignment.getExperimentId()); row.setSpaceId(assignment.getSpaceId());
        row.setAssignmentId(assignment.getId()); row.setActorId(actor); row.setTaskId(assignment.getTaskId()); row.setExecutionId(assignment.getExecutionId()); row.setInputHash(goal.inputSnapshotHash());
        row.setOriginalEvidenceType(type); row.setOriginalEvidenceId(OnlineProtocolUtils.id(evidenceId)); row.setOriginalEvidenceHash(evidenceHash); row.setPolicyVersion(1);
        row.setCapturedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)); row.setSourceHash(hash(row)); receipts.insert(row); return "BLINDED";
    }

    public OnlineBlindReviewReceiptEntity require(OnlineAssignmentEntity assignment, OnlineQualityFeedbackDTO quality, Long actor) {
        var experiment = experiments.selectById(assignment.getExperimentId()); if (!tracked(experiment)) { throw conflict(); }
        var row = find(assignment.getId(), actor, quality.originalEvidenceType());
        verify(row, assignment, quality.originalEvidenceType(), quality.originalEvidenceId(), quality.originalEvidenceHash(), actor); return row;
    }
    private OnlineBlindReviewReceiptEntity find(Long assignment, Long actor, String type) {
        return receipts.selectOne(new LambdaQueryWrapper<OnlineBlindReviewReceiptEntity>().eq(OnlineBlindReviewReceiptEntity::getAssignmentId, assignment)
                .eq(OnlineBlindReviewReceiptEntity::getActorId, actor).eq(OnlineBlindReviewReceiptEntity::getOriginalEvidenceType, type));
    }
    private static boolean tracked(OnlineExperimentEntity experiment) {
        if (experiment == null || !Integer.valueOf(2).equals(experiment.getManifestSchemaVersion())) { return false; }
        try {
            var envelope = OnlineProtocolUtils.object(experiment.getManifestJson());
            return Objects.equals(experiment.getManifestHash(), StableSnapshotUtils.sha256Utf8(experiment.getManifestJson()))
                    && experiment.getManifestJson().equals(OnlineProtocolUtils.canonicalNode(envelope))
                    && "online.manifest".equals(envelope.path("domain").asText()) && envelope.path("schemaVersion").asInt() == 2
                    && experiment.getId().toString().equals(envelope.path("payload").path("experimentId").asText())
                    && experiment.getSpaceId().toString().equals(envelope.path("payload").path("spaceId").asText())
                    && experiment.getAgentId().toString().equals(envelope.path("payload").path("agentId").asText())
                    && envelope.path("payload").path("reviewTrackingSchemaVersion").isIntegralNumber()
                    && envelope.path("payload").path("reviewTrackingSchemaVersion").intValue() == 1;
        } catch (RuntimeException invalid) { return false; }
    }
    private static void verify(OnlineBlindReviewReceiptEntity row, OnlineAssignmentEntity assignment, String type, String evidenceId, String evidenceHash, Long actor) {
        if (row == null || !Integer.valueOf(1).equals(row.getPolicyVersion()) || !Objects.equals(row.getExperimentId(), assignment.getExperimentId())
                || !Objects.equals(row.getSpaceId(), assignment.getSpaceId()) || !Objects.equals(row.getActorId(), actor) || !Objects.equals(row.getAssignmentId(), assignment.getId())
                || !Objects.equals(row.getTaskId(), assignment.getTaskId()) || !Objects.equals(row.getExecutionId(), assignment.getExecutionId())
                || !Objects.equals(row.getInputHash(), assignment.getInputSnapshotHash()) || !Objects.equals(row.getOriginalEvidenceType(), type)
                || !Objects.equals(row.getOriginalEvidenceId(), OnlineProtocolUtils.id(evidenceId)) || !Objects.equals(row.getOriginalEvidenceHash(), evidenceHash)
                || !Objects.equals(row.getSourceHash(), hash(row))) { throw conflict(); }
    }
    public static String hash(OnlineBlindReviewReceiptEntity row) {
        return OnlineProtocolUtils.hash("online.blind-review-receipt", Map.ofEntries(Map.entry("id", row.getId().toString()), Map.entry("experimentId", row.getExperimentId().toString()),
                Map.entry("spaceId", row.getSpaceId().toString()), Map.entry("assignmentId", row.getAssignmentId().toString()), Map.entry("actorId", row.getActorId().toString()),
                Map.entry("taskId", row.getTaskId().toString()), Map.entry("executionId", row.getExecutionId().toString()), Map.entry("inputHash", row.getInputHash()),
                Map.entry("originalEvidenceType", row.getOriginalEvidenceType()), Map.entry("originalEvidenceId", row.getOriginalEvidenceId().toString()),
                Map.entry("originalEvidenceHash", row.getOriginalEvidenceHash()), Map.entry("policyVersion", row.getPolicyVersion()), Map.entry("capturedAt", row.getCapturedAt().toString())));
    }
    private static BusinessException conflict() { return new BusinessException(ErrorCode.CONFLICT, "ONLINE_QUALITY_BLIND_REVIEW_NOT_READY"); }
}
