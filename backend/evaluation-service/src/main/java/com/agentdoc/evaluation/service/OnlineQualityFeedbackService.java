package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineOriginalChangeUtils;
import com.agentdoc.common.utils.OnlineOriginalTextUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.enums.OnlineFeedbackBlindness;
import com.agentdoc.evaluation.enums.EvaluationEvidenceType;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineQualityFeedbackDTO;
import com.agentdoc.evaluation.pojo.vo.EvaluationFeedbackVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;
import static com.agentdoc.common.constant.SpacePermissionConstant.SPACE_READ;
import static com.agentdoc.common.enums.OnlineReasonCode.ONLINE_QUALITY_MULTIPLE_ORIGINALS_NOT_READY;
import static com.agentdoc.common.enums.OnlineReasonCode.ORIGINAL_EVIDENCE_UNAVAILABLE;

/** 当前人类权限与原始证据求证在事务外，只有校验后的追加进入短事务。 */
@Service
@RequiredArgsConstructor
public class OnlineQualityFeedbackService {
    private static final int MAX_COMMENT_LENGTH = 2000;
    private final OnlineAssignmentMapper assignments;
    private final OnlineExperimentMapper experiments;
    private final SpaceAccessService access;
    private final TaskFeign tasks;
    private final AgentFeign agents;
    private final OnlineQualityFeedbackPersistenceService persistence;
    private final OnlineBlindReviewReceiptService receipts;
    private final OnlineSafetyFeedbackService safety;

    public EvaluationFeedbackVO create(EvaluationFeedbackCreateDTO request) {
        EvaluationFeedbackCreateDTO.parse(JsonUtils.toJson(request));
        var quality = request.onlineQuality();
        if (quality == null || request.spaceId() == null || request.label() == null || request.caseRunId() != null
                || request.comment() != null && request.comment().length() > MAX_COMMENT_LENGTH
                || request.score() != null && request.score().compareTo(quality.normalizedScore()) != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "线上质量反馈目标或分数无效");
        }
        Long actor = AuthUtils.getUserIdOrException();
        access.requirePermission(request.spaceId(), SPACE_READ);
        access.requirePermission(request.spaceId(), EVALUATION_RUN);
        var assignment = assignments.selectById(OnlineProtocolUtils.id(quality.assignmentId()));
        if (assignment == null || !Objects.equals(request.spaceId(), assignment.getSpaceId())
                || request.taskId() != null && !Objects.equals(request.taskId(), assignment.getTaskId())
                || request.executionId() != null && !Objects.equals(request.executionId(), assignment.getExecutionId())) { throw conflict(); }
        var experiment = experiments.selectById(assignment.getExperimentId());
        if (experiment == null || !Objects.equals(experiment.getSpaceId(), assignment.getSpaceId())
                || !Integer.valueOf(OnlineProtocolUtils.SCHEMA_VERSION).equals(experiment.getManifestSchemaVersion())
                || !Integer.valueOf(OnlineProtocolUtils.SCHEMA_VERSION).equals(assignment.getBindingSchemaVersion())
                || assignment.getExecutionId() == null) { throw conflict(); }
        if (OnlineFeedbackBlindness.BLINDED.name().equals(quality.blindness())) {
            receipts.require(assignment, quality, actor);
        }
        if (quality.seriousSafetyEvent()) { access.requireOwner(request.spaceId()); }
        var input = data(tasks.getOnlineOriginalInput(assignment.getTaskId(), assignment.getSpaceId(), assignment.getExecutionId()));
        if (!input.completed()) { throw unavailable(); }
        if (!OnlineQualityFeedbackPersistenceService.matches(assignment, input)) { throw conflict(); }
        String identity = originalIdentity(input, quality);
        var result = persistence.append(input, identity, request, actor);
        if (quality.seriousSafetyEvent()) { safety.consume(result.id()); }
        return result;
    }

    private String originalIdentity(OnlineOriginalInputVO input, OnlineQualityFeedbackDTO quality) {
        if (EvaluationEvidenceType.ORIGINAL_TEXT.name().equals(quality.originalEvidenceType())) {
            var original = readOriginalText(input);
            if (!quality.originalEvidenceId().equals(original.evidenceId()) || !quality.originalEvidenceHash().equals(original.contentHash())) { throw conflict(); }
            return original.identityHash();
        }
        var original = data(tasks.getOnlineOriginalChanges(OnlineProtocolUtils.id(input.taskId()), OnlineProtocolUtils.id(input.spaceId()), OnlineProtocolUtils.id(input.executionId())));
        if (!OnlineOriginalChangeUtils.AVAILABLE.equals(original.state())) { throw unavailable(); }
        if (original.schemaVersion() != OnlineProtocolUtils.SCHEMA_VERSION || !Objects.equals(input, original.input())
                || original.items() == null || original.items().isEmpty()) { throw conflict(); }
        if (EvaluationEvidenceType.ORIGINAL_CHANGE_SET.name().equals(quality.originalEvidenceType())) {
            String identity;
            try { identity = OnlineOriginalChangeUtils.setIdentityHash(original); }
            catch (IllegalArgumentException invalid) { throw conflict(); }
            if (!Objects.equals(input.assignmentId(), quality.originalEvidenceId()) || !Objects.equals(identity, quality.originalEvidenceHash())
                    || !Objects.equals(input, data(tasks.getOnlineOriginalInput(OnlineProtocolUtils.id(input.taskId()),
                        OnlineProtocolUtils.id(input.spaceId()), OnlineProtocolUtils.id(input.executionId()))))) { throw conflict(); }
            return identity;
        }
        if (original.items().size() != 1) { throw new BusinessException(ErrorCode.CONFLICT, ONLINE_QUALITY_MULTIPLE_ORIGINALS_NOT_READY.name()); }
        var item = original.items().getFirst();
        if (!OnlineOriginalChangeUtils.valid(input, item) || !quality.originalEvidenceType().equals(item.outputType())
                || !quality.originalEvidenceId().equals(item.evidenceId()) || !quality.originalEvidenceHash().equals(item.payloadHash())
                || !Objects.equals(input.baseVersion(), item.baseVersion())
                || (OnlineOriginalChangeUtils.DRAFT.equals(item.outputType())
                    ? !Objects.equals(item.outputVersion(), Math.addExact(input.baseVersion(), 1L)) : item.outputVersion() != null)) { throw conflict(); }
        return item.identityHash();
    }

    /** 原始文本评审与评分共用来源身份核验，正文只留在受控读取链路。 */
    AgentOnlineOriginalTextVO readOriginalText(OnlineOriginalInputVO input) {
        var original = data(agents.getOnlineOriginalText(OnlineProtocolUtils.id(input.taskId()), OnlineProtocolUtils.id(input.spaceId())));
        if (!OnlineOriginalTextUtils.AVAILABLE.equals(original.state())) { throw unavailable(); }
        if (!OnlineOriginalTextUtils.valid(original) || !Objects.equals(original.taskId(), input.taskId())
                || !Objects.equals(original.executionId(), input.executionId()) || !Objects.equals(original.spaceId(), input.spaceId())
                || !Objects.equals(original.agentId(), input.agentId()) || !Objects.equals(original.experimentId(), input.experimentId())
                || !Objects.equals(original.assignmentId(), input.assignmentId()) || !Objects.equals(original.bindingHash(), input.bindingHash())) { throw conflict(); }
        return original;
    }

    private static <T> T data(Result<T> response) {
        if (response == null || response.code() != ErrorCode.SUCCESS.getCode() || response.data() == null) { throw unavailable(); }
        return response.data();
    }
    private static BusinessException unavailable() { return new BusinessException(ErrorCode.CONFLICT, ORIGINAL_EVIDENCE_UNAVAILABLE.name()); }
    private static BusinessException conflict() { return new BusinessException(ErrorCode.CONFLICT, "线上质量反馈原始身份不一致"); }
}
