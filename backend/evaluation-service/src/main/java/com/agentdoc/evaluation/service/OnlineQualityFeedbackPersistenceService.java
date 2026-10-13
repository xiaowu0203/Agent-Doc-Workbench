package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.agentdoc.evaluation.enums.EvaluationFeedbackSourceType;
import com.agentdoc.evaluation.enums.EvaluationEvidenceType;
import com.agentdoc.evaluation.enums.OnlineFeedbackBlindness;
import com.agentdoc.evaluation.mapper.EvaluationFeedbackMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluationFeedbackEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.vo.EvaluationFeedbackVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;

/** 原始反馈与显式更正共用 assignment 锁，事务内只访问评价领域。 */
@Service
@RequiredArgsConstructor
public class OnlineQualityFeedbackPersistenceService {
    private static final String HASH_DOMAIN = "online.quality-feedback";
    private final OnlineAssignmentMapper assignments;
    private final EvaluationFeedbackMapper feedback;
    private final OnlineExperimentMapper experiments;
    private final OnlineReviewExposureService exposures;
    private final OnlineBlindReviewReceiptService receipts;

    @Transactional(rollbackFor = Exception.class)
    public EvaluationFeedbackVO append(OnlineOriginalInputVO input, String identityHash, EvaluationFeedbackCreateDTO request, Long actor) {
        var experiment = experiments.lock(OnlineProtocolUtils.id(input.experimentId()));
        if (experiment == null || !Objects.equals(experiment.getSpaceId().toString(), input.spaceId())) { throw conflict(); }
        var assignment = assignments.lockTask(OnlineProtocolUtils.id(input.taskId()));
        if (!matches(assignment, input) || !input.completed()) { throw conflict(); }
        var quality = request.onlineQuality(); quality.validate();
        if (!Objects.equals(input.assignmentId(), quality.assignmentId()) || !Objects.equals(assignment.getSpaceId(), request.spaceId())
                || identityHash == null || !identityHash.matches("[0-9a-f]{64}") || actor == null || actor <= 0
                || request.caseRunId() != null || request.label() == null
                || request.score() != null && request.score().compareTo(quality.normalizedScore()) != 0) { throw conflict(); }
        if (EvaluationEvidenceType.ORIGINAL_CHANGE_SET.name().equals(quality.originalEvidenceType())
                && (!Objects.equals(input.assignmentId(), quality.originalEvidenceId()) || !Objects.equals(identityHash, quality.originalEvidenceHash()))) { throw conflict(); }
        var rows = feedback.selectList(new LambdaQueryWrapper<EvaluationFeedbackEntity>()
                .eq(EvaluationFeedbackEntity::getOnlineAssignmentId, assignment.getId())
                .eq(EvaluationFeedbackEntity::getSourceType, EvaluationFeedbackSourceType.MANUAL.name())
                .isNotNull(EvaluationFeedbackEntity::getRubricScore));
        var exposure = exposures.known(experiment.getId(), actor);
        var receipt = OnlineFeedbackBlindness.BLINDED.name().equals(quality.blindness()) ? receipts.require(assignment, quality, actor) : null;
        if (exposure != null && !Objects.equals(exposure.getSpaceId(), assignment.getSpaceId())) { throw conflict(); }
        EvaluationFeedbackEntity predecessor = null;
        if (quality.supersedesFeedbackId() == null) {
            if (!rows.isEmpty()) { throw conflict(); }
        } else {
            if ((request.comment() == null || request.comment().isBlank()) && quality.reasonCodes().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "显式更正必须提供理由");
            }
            for (var row : rows) {
                if (!Objects.equals(row.getSpaceId(), assignment.getSpaceId()) || !Objects.equals(row.getTaskId(), assignment.getTaskId())
                        || !Objects.equals(row.getExecutionId(), assignment.getExecutionId()) || row.getRunId() != null || row.getCaseRunId() != null
                        || !Objects.equals(row.getOriginalEvidenceId(), OnlineProtocolUtils.id(quality.originalEvidenceId()))
                        || !Objects.equals(row.getOriginalEvidenceHash(), quality.originalEvidenceHash())) { throw conflict(); }
                var facts = JsonUtils.parse(row.getFactsJson(), JsonNode.class);
                if (facts == null || !Objects.equals(facts.path("originalEvidenceType").asText(), quality.originalEvidenceType())
                        || !Objects.equals(facts.path("originalIdentityHash").asText(), identityHash)
                        || !Objects.equals(facts.path("input"), JsonUtils.parse(JsonUtils.toJson(input), JsonNode.class))) { throw conflict(); }
            }
            var chain = OnlineQualityFeedbackFacts.orderedChain(rows);
            if (chain == null || chain.isEmpty()) { throw conflict(); }
            predecessor = chain.getLast();
            if (!Objects.equals(predecessor.getId(), OnlineProtocolUtils.id(quality.supersedesFeedbackId()))) { throw conflict(); }
        }
        var entity = new EvaluationFeedbackEntity(); entity.setId(IdWorker.getId());
        entity.setSpaceId(assignment.getSpaceId()); entity.setTaskId(assignment.getTaskId()); entity.setExecutionId(assignment.getExecutionId());
        entity.setSourceType(EvaluationFeedbackSourceType.MANUAL.name()); entity.setSourceBusinessId(entity.getId().toString());
        entity.setOnlineAssignmentId(assignment.getId()); entity.setOriginalEvidenceId(OnlineProtocolUtils.id(quality.originalEvidenceId()));
        entity.setOriginalEvidenceHash(quality.originalEvidenceHash()); entity.setRubricScore(quality.rubricScore());
        entity.setBlindness(exposure != null || predecessor != null && OnlineFeedbackBlindness.NON_BLINDED.name().equals(predecessor.getBlindness())
                ? OnlineFeedbackBlindness.NON_BLINDED.name() : quality.blindness());
        entity.setSupersedesFeedbackId(predecessor == null ? null : predecessor.getId());
        entity.setReasonCodesJson(JsonUtils.toJson(quality.reasonCodes())); entity.setSeriousSafetyEvent(quality.seriousSafetyEvent());
        entity.setSafetyReason(quality.safetyReason()); entity.setLabel(request.label().name()); entity.setScore(quality.normalizedScore());
        entity.setComment(request.comment()); entity.setCreatedBy(actor);
        var facts = new LinkedHashMap<String, Object>(Map.of("qualitySchemaVersion", OnlineProtocolUtils.SCHEMA_VERSION,
                "originalEvidenceType", quality.originalEvidenceType(), "originalIdentityHash", identityHash,
                "declaredBlindness", quality.blindness(), "input", input));
        var hashInput = new LinkedHashMap<String, Object>(Map.ofEntries(
                Map.entry("feedbackId", entity.getId().toString()), Map.entry("input", input), Map.entry("quality", quality),
                Map.entry("originalIdentityHash", identityHash), Map.entry("effectiveBlindness", entity.getBlindness()),
                Map.entry("label", entity.getLabel()), Map.entry("score", entity.getScore().toPlainString()),
                Map.entry("comment", entity.getComment() == null ? "" : entity.getComment()), Map.entry("actorId", actor.toString())));
        if (exposure != null) {
            facts.put("exposureId", exposure.getId().toString()); facts.put("exposureHash", exposure.getSourceHash());
            hashInput.put("exposureId", exposure.getId().toString()); hashInput.put("exposureHash", exposure.getSourceHash());
        }
        if (receipt != null) {
            facts.put("reviewReceiptId", receipt.getId().toString()); facts.put("reviewReceiptHash", receipt.getSourceHash());
            hashInput.put("reviewReceiptId", receipt.getId().toString()); hashInput.put("reviewReceiptHash", receipt.getSourceHash());
        }
        entity.setFactsJson(JsonUtils.toJson(facts));
        entity.setSourceHash(OnlineProtocolUtils.hash(receipt != null ? "online.quality-feedback-with-review-proof" : exposure == null ? HASH_DOMAIN : "online.quality-feedback-with-exposure", hashInput));
        feedback.insert(entity);
        return EvaluationFeedbackVO.from(entity);
    }

    /** 求证与落库使用同一绑定身份规则；不读取分组或当前正文。 */
    public static boolean matches(OnlineAssignmentEntity row, OnlineOriginalInputVO input) {
        if (row == null || input == null || !Integer.valueOf(OnlineProtocolUtils.SCHEMA_VERSION).equals(row.getBindingSchemaVersion())) { return false; }
        return Objects.equals(text(row.getId()), input.assignmentId()) && Objects.equals(text(row.getTaskId()), input.taskId())
                && row.getExecutionId() != null && Objects.equals(text(row.getExecutionId()), input.executionId())
                && Objects.equals(text(row.getSpaceId()), input.spaceId()) && Objects.equals(text(row.getAgentId()), input.agentId())
                && Objects.equals(text(row.getExperimentId()), input.experimentId()) && Objects.equals(text(row.getDocumentId()), input.documentId())
                && row.getBindingHash() != null && Objects.equals(row.getBindingHash(), input.bindingHash())
                && input.bindingHash().matches("[0-9a-f]{64}") && input.inputHash() != null && input.inputHash().matches("[0-9a-f]{64}")
                && input.baseVersion() != null && input.baseVersion() >= 0;
    }
    private static String text(Long value) { return value == null ? null : value.toString(); }
    private static BusinessException conflict() { return new BusinessException(ErrorCode.CONFLICT, "线上质量反馈身份或更正链冲突"); }
}
