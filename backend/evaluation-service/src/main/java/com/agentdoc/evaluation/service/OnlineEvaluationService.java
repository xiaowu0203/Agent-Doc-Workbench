package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.evaluator.DeterministicEvaluationOutcome;
import com.agentdoc.evaluation.evaluator.OnlineOriginalTextEvaluator;
import com.agentdoc.evaluation.evaluator.OnlineRuleContractValidator;
import com.agentdoc.evaluation.enums.EvaluationResultStatus;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.pojo.dto.OnlineEvaluationCreateDTO;
import com.agentdoc.evaluation.pojo.vo.OnlineEvaluationVO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;
import static com.agentdoc.common.constant.SpacePermissionConstant.SPACE_READ;
import static com.agentdoc.common.enums.OnlineReasonCode.*;

/** 评价原执行的冻结规则；不触发模型、重跑或修改 Task/Execution 终态。 */
@Service
@RequiredArgsConstructor
public class OnlineEvaluationService {
    private final OnlineExperimentMapper experiments;
    private final OnlineAssignmentMapper assignments;
    private final EvaluatorVersionMapper versions;
    private final SpaceAccessService access;
    private final TaskFeign tasks;
    private final AgentFeign agents;
    private final OnlineOriginalTextEvaluator evaluator;
    private final OnlineEvaluationPersistenceService persistence;

    public OnlineEvaluationVO evaluate(String experimentId, String assignmentId, OnlineEvaluationCreateDTO request) {
        OnlineEvaluationCreateDTO.parse(JsonUtils.toJson(request));
        Long actor = AuthUtils.getUserIdOrException();
        var experiment = experiments.selectById(OnlineProtocolUtils.id(experimentId));
        if (experiment == null) { throw invalid(ErrorCode.NOT_FOUND, "实验不存在"); }
        access.requirePermission(experiment.getSpaceId(), SPACE_READ); access.requirePermission(experiment.getSpaceId(), EVALUATION_RUN);
        var assignment = assignments.selectById(OnlineProtocolUtils.id(assignmentId));
        if (assignment == null || !Objects.equals(assignment.getExperimentId(), experiment.getId())
                || !Objects.equals(assignment.getSpaceId(), experiment.getSpaceId())) { throw invalid(ErrorCode.NOT_FOUND, "分配不存在"); }
        if (!Integer.valueOf(2).equals(experiment.getManifestSchemaVersion()) || !Integer.valueOf(2).equals(assignment.getBindingSchemaVersion())
                || assignment.getExecutionId() == null) { throw invalid(ErrorCode.CONFLICT, ONLINE_EVALUATION_SUBJECT_NOT_READY.name()); }
        var permission = tasks.checkOriginalEvidencePermission(assignment.getTaskId(), assignment.getSpaceId(), assignment.getExecutionId());
        if (permission == null || permission.code() != ErrorCode.SUCCESS.getCode()) { throw invalid(ErrorCode.FORBIDDEN, ACCESS_DENIED.name()); }
        var envelope = OnlineProtocolUtils.object(experiment.getManifestJson());
        if (!"online.manifest".equals(envelope.path("domain").asText()) || envelope.path("schemaVersion").asInt() != 2
                || !StableSnapshotUtils.sha256Utf8(experiment.getManifestJson()).equals(experiment.getManifestHash())) {
            throw invalid(ErrorCode.CONFLICT, MANIFEST_INVALID.name());
        }
        var rules = envelope.path("payload").path("ruleBindings");
        var matched = StreamSupport.stream(rules.spliterator(), false).filter(rule -> request.ruleKey().equals(rule.path("binding").path("ruleKey").asText())).toList();
        if (matched.size() != 1) { throw invalid(ErrorCode.BAD_REQUEST, ONLINE_RULE_INVALID.name()); }
        JsonNode rule = matched.getFirst(), binding = rule.path("binding");
        if (!OnlineRuleContractValidator.TEXT.equals(rule.path("evaluatorKey").asText())
                || !"ORIGINAL_TEXT".equals(binding.path("evidenceTarget").asText())) { throw invalid(ErrorCode.CONFLICT, ONLINE_RULE_NOT_READY.name()); }
        Long versionId = OnlineProtocolUtils.id(binding.path("evaluatorVersionId").asText());
        var version = versions.selectById(versionId);
        if (version == null || !"PUBLISHED".equals(version.getStatus()) || !Objects.equals(version.getSpaceId(), experiment.getSpaceId())
                || !OnlineRuleContractValidator.TEXT.equals(version.getEvaluatorKey()) || !Objects.equals(version.getContentHash(), rule.path("contentHash").asText())
                || !OnlineOriginalTextEvaluator.IMPLEMENTATION_VERSION.equals(version.getImplementationVersion())
                || !Objects.equals(version.getContentHash(), OnlineExperimentService.versionHash(version))
                || !Integer.valueOf(1).equals(version.getConfigSchemaVersion()) || !Integer.valueOf(1).equals(version.getResultSchemaVersion())) {
            throw invalid(ErrorCode.CONFLICT, ONLINE_RULE_NOT_READY.name());
        }
        var expectedRows = StreamSupport.stream(binding.path("expectedBindings").spliterator(), false)
                .filter(row -> assignment.getDocumentId().toString().equals(row.path("documentId").asText())).toList();
        if (expectedRows.size() != 1) { throw invalid(ErrorCode.CONFLICT, EXPECTED_MAPPING_MISSING.name()); }
        JsonNode expected = expectedRows.getFirst().path("expectedJson");
        String expectedHash = OnlineProtocolUtils.hash("online.expected", expected);
        String requestHash = OnlineProtocolUtils.hash("online.evaluation-request", Map.of("experimentId", experimentId,
                "assignmentId", assignmentId, "actorId", actor.toString(), "ruleKey", request.ruleKey(),
                "evaluatorVersionId", versionId.toString(), "versionHash", version.getContentHash(), "expectedHash", expectedHash));
        var prior = persistence.prior(assignment.getId(), versionId, request.clientRequestKey());
        if (prior != null) { return persistence.reuse(prior, requestHash); }
        var response = agents.getOnlineOriginalText(assignment.getTaskId(), assignment.getSpaceId());
        if (response == null || response.code() != ErrorCode.SUCCESS.getCode() || response.data() == null) {
            throw invalid(ErrorCode.CONFLICT, ORIGINAL_EVIDENCE_UNAVAILABLE.name());
        }
        var original = response.data();
        if (!assignment.getTaskId().toString().equals(original.taskId()) || !assignment.getExecutionId().toString().equals(original.executionId())
                || !assignment.getSpaceId().toString().equals(original.spaceId()) || !assignment.getAgentId().toString().equals(original.agentId())
                || !experimentId.equals(original.experimentId()) || !assignmentId.equals(original.assignmentId()) || !Objects.equals(assignment.getBindingHash(), original.bindingHash())) {
            throw invalid(ErrorCode.CONFLICT, ORIGINAL_EVIDENCE_INVALID.name());
        }
        LocalDateTime start = LocalDateTime.now();
        DeterministicEvaluationOutcome outcome;
        try { outcome = evaluator.evaluate(version.getConfigJson(), JsonUtils.toJson(expected), original); }
        catch (RuntimeException failed) {
            outcome = new DeterministicEvaluationOutcome(EvaluationResultStatus.ERROR, ONLINE_RULE_ERROR.name(), "{}", List.of(), List.of());
        }
        return persistence.append(assignment, versionId, request.ruleKey(), request.clientRequestKey(), requestHash, expectedHash, actor, start, outcome);
    }
    private static BusinessException invalid(ErrorCode code, String reason) { return new BusinessException(code, reason); }
}
