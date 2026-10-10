package com.agentdoc.evaluation.service;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentOnlineConfigFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.AgentOnlineConfigPrepareDTO;
import com.agentdoc.common.feign.vo.AgentOnlineConfigPairVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.evaluation.evaluator.EvaluatorContractValidator;
import com.agentdoc.evaluation.evaluator.OnlineRuleContractValidator;
import com.agentdoc.evaluation.mapper.EvaluatorVersionMapper;
import com.agentdoc.evaluation.mapper.OnlineExperimentMapper;
import com.agentdoc.evaluation.mapper.OnlineAssignmentMapper;
import com.agentdoc.evaluation.pojo.entity.OnlineAssignmentEntity;
import com.agentdoc.evaluation.pojo.param.OnlineAssignmentSearchParam;
import com.agentdoc.evaluation.pojo.vo.OnlineAssignmentVO;
import com.agentdoc.evaluation.convertor.OnlineAssignmentConvertor;
import com.agentdoc.evaluation.convertor.OnlineExperimentConvertor;
import com.agentdoc.evaluation.mapper.OnlineExperimentCreateIntentMapper;
import com.agentdoc.evaluation.metric.MetricDefinitionCatalog;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentCreateDTO;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.agentdoc.evaluation.pojo.entity.OnlineExperimentCreateIntentEntity;
import com.agentdoc.evaluation.pojo.param.OnlineExperimentSearchParam;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentVO;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentSummaryVO;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentPreflightVO;
import com.agentdoc.common.pojo.vo.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.*;
import static com.agentdoc.evaluation.constant.OnlineExperimentConstant.*;
import static com.agentdoc.evaluation.service.OnlineExperimentRequestValidator.id;

/** P6-01 创建与只读预检；不提供启动、分配、写结果或模型入口。 */
@Service
@RequiredArgsConstructor
public class OnlineExperimentService {
    private final OnlineExperimentMapper mapper;
    private final OnlineAssignmentMapper assignmentMapper;
    private final OnlineAssignmentConvertor assignmentConvertor;
    private final OnlineExperimentConvertor convertor;
    private final OnlineExperimentCreateIntentMapper intentMapper;
    private final OnlineExperimentPersistenceService persistence;
    private final EvaluatorVersionMapper evaluatorVersionMapper;
    private final EvaluatorContractValidator ruleValidator;
    private final SpaceAccessService access;
    private final AgentOnlineConfigFeign agentFeign;
    private final DocumentFeign documentFeign;
    private final OnlinePreflightProofService preflightProofs;

    public OnlineExperimentVO create(OnlineExperimentCreateDTO raw) {
        var request = OnlineExperimentRequestValidator.normalize(raw);
        long spaceId = id(request.spaceId());
        access.requireOwner(spaceId);
        access.requirePermission(spaceId, EVALUATION_MANAGE);
        access.requirePermission(spaceId, AGENT_MANAGE);
        Long actor = AuthUtils.getUserIdOrException();
        ObjectNode requestPayload = (ObjectNode) JsonUtils.parseStrict(JsonUtils.toJson(request), JsonNode.class);
        requestPayload.remove("clientRequestKey");
        requestPayload.put("creatorId", actor.toString());
        String requestHash = OnlineProtocolUtils.hash("online.create-request", requestPayload);
        var intent = intent(spaceId, actor, request.clientRequestKey());
        if (intent != null) {
            same(intent.getRequestHash(), requestHash);
            var existing = mapper.selectById(intent.getId());
            if (existing != null) { requireSchema(existing); return view(existing); }
        } else {
            intent = new OnlineExperimentCreateIntentEntity();
            intent.setId(IdWorker.getId());
            intent.setSpaceId(spaceId);
            intent.setCreatedBy(actor);
            intent.setRequestKey(request.clientRequestKey());
            intent.setRequestHash(requestHash);
            intent.setStatus("PREPARING");
            try { persistence.reserve(intent); }
            catch (DuplicateKeyException race) {
                intent = intent(spaceId, actor, request.clientRequestKey());
                if (intent == null) { throw race; }
                same(intent.getRequestHash(), requestHash);
            }
        }
        // 网络调用均在短事务之外；失败重试继续使用同一实验身份。
        requireScope(spaceId, request.documentIds());
        var frozenRules = rules(spaceId, request);
        var pair = data(agentFeign.prepareOnlineConfigs(new AgentOnlineConfigPrepareDTO(
                intent.getId().toString(), request.spaceId(), request.agentId(), requestHash, request.candidateAgentPrompt())));
        requirePair(pair);
        byte[] seedBytes = new byte[16];
        new SecureRandom().nextBytes(seedBytes);
        String seed = HexFormat.of().formatHex(seedBytes);
        Map<String, Object> payload = new TreeMap<>();
        payload.put("experimentId", intent.getId().toString());
        payload.put("spaceId", request.spaceId());
        payload.put("agentId", request.agentId());
        payload.put("name", request.name());
        payload.put("createdBy", actor.toString());
        payload.put("documentIds", request.documentIds());
        payload.put("bucketProtocol", Map.of("schemaVersion", OnlineProtocolUtils.SCHEMA_VERSION,
                "seed", seed, "weight", request.candidateWeightBps()));
        payload.put("baseline", pair.baseline());
        payload.put("candidate", pair.candidate());
        payload.put("dependencyHash", pair.dependencyHash());
        payload.put("ruleBindings", frozenRules);
        payload.put("analysisPlan", Map.of("mode", request.analysisPlan().mode().name(),
                "mdeAbsoluteRatio", request.analysisPlan().mdeAbsoluteRatio(), "mdeReason", request.analysisPlan().mdeReason(),
                "humanQualityRequired", request.analysisPlan().humanQualityRequired(), "minDocumentsPerGroup", MIN_DOCUMENTS_PER_GROUP,
                "minCoverageBps", MIN_COVERAGE_BPS, "minBlindedCoverageBps", MIN_COVERAGE_BPS));
        payload.put("budgetPlan", Map.of("total", request.authorizedTokenBudget(), "maxTask", request.maxTaskCount(),
                "perTask", request.perTaskTokenLimit(), "timeout", pair.executionTimeoutSeconds()));
        payload.put("resourcePlan", Map.of("perVariantLimit", 1));
        payload.put("windowPlan", Map.of("assignmentWindowSeconds", request.assignmentWindowSeconds(),
                "completionObservationSeconds", request.completionObservationSeconds()));
        payload.put("protectionPlan", Map.of("healthWindowCount", HEALTH_WINDOW_COUNT, "healthFailureBps", HEALTH_FAILURE_BPS,
                "unresolvedSeconds", UNRESOLVED_SECONDS, "srmMinDocuments", SRM_MIN_DOCUMENTS,
                "srmMinExpectedCount", SRM_MIN_EXPECTED_COUNT, "srmThreshold", SRM_THRESHOLD, "srmRecheckSeconds", UNRESOLVED_SECONDS));
        payload.put("disclosureSchemaVersion", OnlineProtocolUtils.SCHEMA_VERSION);
        String canonical = OnlineProtocolUtils.canonical("online.manifest", payload);
        if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_MANIFEST_BYTES) { throw conflict(MANIFEST_TOO_LARGE.name()); }
        var entity = new OnlineExperimentEntity();
        entity.setId(intent.getId());
        entity.setSpaceId(spaceId);
        entity.setAgentId(id(request.agentId()));
        entity.setName(request.name());
        entity.setClientRequestKey(request.clientRequestKey());
        entity.setRequestHash(requestHash);
        entity.setManifestSchemaVersion(OnlineProtocolUtils.SCHEMA_VERSION);
        entity.setManifestJson(canonical);
        entity.setManifestHash(StableSnapshotUtils.sha256Utf8(canonical));
        entity.setStatus("CREATED");
        entity.setStateVersion(0L);
        entity.setAcceptedSequence(0L);
        entity.setAuthorizedTokenBudget(id(request.authorizedTokenBudget()));
        entity.setMaxTaskCount(request.maxTaskCount());
        entity.setAssignedTaskCount(0);
        entity.setReservedTokenBudget(0L);
        entity.setConsumedTokens(BigInteger.ZERO);
        entity.setBaselineSlotCount(0);
        entity.setCandidateSlotCount(0);
        entity.setUnknownTaskCount(0);
        entity.setCreatedBy(actor);
        try { persistence.complete(entity); }
        catch (DuplicateKeyException race) {
            var concurrent = mapper.selectById(intent.getId());
            if (concurrent == null) { throw race; }
            same(concurrent.getRequestHash(), requestHash);
            requireSchema(concurrent);
            return view(concurrent);
        }
        return view(entity);
    }

    public OnlineExperimentVO detail(String identity) {
        var entity = require(id(identity));
        read(entity.getSpaceId());
        return view(entity);
    }

    public PageVO<OnlineExperimentSummaryVO> search(OnlineExperimentSearchParam param) {
        param.validate();
        long spaceId = id(param.getSpaceId());
        read(spaceId);
        var query = new LambdaQueryWrapper<OnlineExperimentEntity>()
                .eq(OnlineExperimentEntity::getSpaceId, spaceId)
                .eq(param.getAgentId() != null, OnlineExperimentEntity::getAgentId,
                        param.getAgentId() == null ? null : id(param.getAgentId()))
                .eq(param.getStatus() != null, OnlineExperimentEntity::getStatus, param.getStatus())
                .like(param.getKeyword() != null && !param.getKeyword().isBlank(), OnlineExperimentEntity::getName, param.getKeyword())
                .orderByDesc(OnlineExperimentEntity::getId);
        var page = mapper.selectPage(new Page<>(param.getPageNum(), param.getPageSize()), query);
        var ids = page.getRecords().stream().map(OnlineExperimentEntity::getId).toList();
        var counts = ids.isEmpty() ? Map.<Long, Long>of() : assignmentMapper.participation(ids).stream()
                .collect(Collectors.toMap(value -> value.experimentId(), value -> value.documentCount()));
        return PageVO.of(page.getRecords().stream().map(value -> summary(value, counts.getOrDefault(value.getId(), 0L)))
                .toList(), page.getTotal(), param);
    }

    public PageVO<OnlineAssignmentVO> assignments(String identity, OnlineAssignmentSearchParam param) {
        var entity = require(id(identity));
        read(entity.getSpaceId());
        requireSchema(entity);
        param.validate();
        var query = new LambdaQueryWrapper<OnlineAssignmentEntity>()
                .eq(OnlineAssignmentEntity::getExperimentId, entity.getId())
                .eq(OnlineAssignmentEntity::getSpaceId, entity.getSpaceId())
                .eq(OnlineAssignmentEntity::getBindingSchemaVersion, OnlineProtocolUtils.SCHEMA_VERSION)
                .eq(param.getVariant() != null, OnlineAssignmentEntity::getVariant, param.getVariant())
                .eq(param.getDocumentId() != null, OnlineAssignmentEntity::getDocumentId,
                        param.getDocumentId() == null ? null : id(param.getDocumentId()))
                .eq(param.getTaskId() != null, OnlineAssignmentEntity::getTaskId,
                        param.getTaskId() == null ? null : id(param.getTaskId()))
                .eq(param.getConfirmationStatus() != null, OnlineAssignmentEntity::getTaskConfirmationStatus, param.getConfirmationStatus())
                .eq(param.getSettlementStatus() != null, OnlineAssignmentEntity::getSettlementStatus, param.getSettlementStatus())
                .orderByDesc(OnlineAssignmentEntity::getId);
        var page = assignmentMapper.selectPage(new Page<>(param.getPageNum(), param.getPageSize()), query);
        return PageVO.of(page.getRecords().stream().map(assignmentConvertor::toVO).toList(), page.getTotal(), param);
    }

    public OnlineExperimentPreflightVO preflight(String identity) {
        var entity = require(id(identity));
        access.requireOwner(entity.getSpaceId());
        access.requirePermission(entity.getSpaceId(), EVALUATION_READ);
        var issues = new ArrayList<OnlineExperimentPreflightVO.Issue>();
        Instant checked = Instant.now();
        if (!Objects.equals(entity.getManifestSchemaVersion(), OnlineProtocolUtils.SCHEMA_VERSION)) {
            return new OnlineExperimentPreflightVO(identity, entity.getManifestHash(), checked.toString(), null,
                    checked.plusSeconds(PREFLIGHT_SECONDS).toString(), false, false, 0, 0, null, null, null,
                    List.of(issue(UNSUPPORTED_ONLINE_SCHEMA.name(), "ERROR")));
        }
        JsonNode payload = manifest(entity);
        List<String> documents = documents(payload);
        try { requireScope(entity.getSpaceId(), documents); }
        catch (BusinessException changed) { issues.add(issue(SCOPE_DRIFT.name(), "ERROR")); }
        String currentDependency = null;
        try {
            currentDependency = data(agentFeign.onlineDependency(entity.getId(), entity.getSpaceId()));
            if (!payload.path("dependencyHash").asText().equals(currentDependency)) { issues.add(issue(DEPENDENCY_DRIFT.name(), "ERROR")); }
        } catch (RuntimeException unavailable) { issues.add(issue(DEPENDENCY_UNAVAILABLE.name(), "ERROR")); }
        if (mapper.selectCount(new LambdaQueryWrapper<OnlineExperimentEntity>()
                .eq(OnlineExperimentEntity::getSpaceId, entity.getSpaceId())
                .isNotNull(OnlineExperimentEntity::getActiveSlot).ne(OnlineExperimentEntity::getId, entity.getId())) > 0) {
            issues.add(issue(SPACE_SLOT_OCCUPIED.name(), "ERROR"));
        }
        // 批量复查冻结规则版本，不能以已发布契约元数据冒充 LIVE 引擎。
        List<Long> versionIds = new ArrayList<>();
        payload.path("ruleBindings").forEach(rule -> versionIds.add(id(rule.path("binding").path("evaluatorVersionId").asText())));
        var versions = evaluatorVersionMapper.selectBatchIds(versionIds).stream()
                .collect(Collectors.toMap(EvaluatorVersionEntity::getId, Function.identity()));
        payload.path("ruleBindings").forEach(rule -> {
            var version = versions.get(id(rule.path("binding").path("evaluatorVersionId").asText()));
            if (version == null || !entity.getSpaceId().equals(version.getSpaceId()) || !"PUBLISHED".equals(version.getStatus())
                    || !rule.path("contentHash").asText().equals(version.getContentHash())
                    || !Objects.equals(version.getContentHash(), versionHash(version))) {
                issues.add(new OnlineExperimentPreflightVO.Issue(ONLINE_RULE_NOT_READY.name(), "ERROR", null,
                        rule.path("binding").path("ruleKey").asText()));
            }
        });
        int weight = payload.path("bucketProtocol").path("weight").asInt();
        String seed = payload.path("bucketProtocol").path("seed").asText();
        int candidate = (int) documents.stream().filter(document -> OnlineProtocolUtils.variant(
                OnlineProtocolUtils.bucket(identity, document, seed), weight).equals("CANDIDATE")).count();
        var srm = OnlineExperimentStatistics.srm(documents.size(), candidate, weight);
        if ("DETECTED".equals(srm.status())) { issues.add(issue(SRM_DETECTED.name(), "ERROR")); }
        else if ("NOT_ENOUGH_UNITS".equals(srm.status())) { issues.add(issue(SRM_NOT_ENOUGH_UNITS.name(), "WARNING")); }
        if (candidate == 0 || candidate == documents.size()) { issues.add(issue(RANGE_MISSING_VARIANT.name(), "WARNING")); }
        boolean draftEligible = issues.stream().noneMatch(value -> value.severity().equals("ERROR"));
        issues.add(issue(ONLINE_EXECUTION_NOT_READY.name(), "ERROR"));
        issues.add(issue(ONLINE_RULE_NOT_READY.name(), "ERROR"));
        issues.add(issue(ONLINE_REPORT_NOT_READY.name(), "ERROR"));
        issues.add(issue(RETENTION_CONFIRMATION_REQUIRED.name(), "WARNING"));
        issues.add(issue(HISTORICAL_ESTIMATE_UNAVAILABLE.name(), "WARNING"));
        Map<String, Object> proof = new TreeMap<>();
        proof.put("experimentId", identity);
        proof.put("actorId", AuthUtils.getUserIdOrException().toString());
        proof.put("manifestHash", entity.getManifestHash());
        proof.put("dependencyHash", currentDependency);
        proof.put("stateVersion", entity.getStateVersion());
        proof.put("checkedAt", checked.toString());
        String upper = BigInteger.valueOf(entity.getMaxTaskCount()).multiply(
                new BigInteger(payload.path("budgetPlan").path("perTask").asText())).toString();
        String proofHash = OnlineProtocolUtils.hash("online.preflight", proof);
        preflightProofs.save(entity, currentDependency, proofHash, checked.plusSeconds(PREFLIGHT_SECONDS));
        return new OnlineExperimentPreflightVO(identity, entity.getManifestHash(), checked.toString(),
                proofHash, checked.plusSeconds(PREFLIGHT_SECONDS).toString(),
                draftEligible, false, documents.size() - candidate, candidate, srm, upper, null, List.copyOf(issues));
    }

    private List<Map<String, Object>> rules(Long spaceId, OnlineExperimentCreateDTO request) {
        var versions = evaluatorVersionMapper.selectBatchIds(request.rules().stream()
                .map(rule -> id(rule.evaluatorVersionId())).distinct().toList()).stream()
                .collect(Collectors.toMap(EvaluatorVersionEntity::getId, Function.identity()));
        return request.rules().stream().map(rule -> {
            var version = versions.get(id(rule.evaluatorVersionId()));
            if (version == null || !spaceId.equals(version.getSpaceId()) || !"PUBLISHED".equals(version.getStatus())
                    || version.getContentHash() == null || !version.getContentHash().equals(versionHash(version))) {
                throw conflict(ONLINE_RULE_NOT_READY.name());
            }
            var metric = MetricDefinitionCatalog.require(rule.metricKey());
            if (!Objects.equals(metric.evaluatorKey(), version.getEvaluatorKey()) || metric.valueType() != rule.valueType()
                    || !metric.unit().equals(rule.unit())) { throw conflict(ONLINE_RULE_INVALID.name()); }
            String expectedTarget = switch (version.getEvaluatorKey()) {
                case OnlineRuleContractValidator.TEXT -> "ORIGINAL_TEXT";
                case OnlineRuleContractValidator.CHANGE -> "ORIGINAL_CHANGE";
                case "task-terminal-status" -> "TASK_FACTS";
                case "audit-ledger-integrity" -> "AUDIT_FACTS";
                default -> throw conflict(ONLINE_RULE_NOT_LIVE_COMPATIBLE.name());
            };
            if (!rule.evidenceTarget().name().equals(expectedTarget)) { throw conflict(ONLINE_RULE_INVALID.name()); }
            ruleValidator.validateVersion(version.getEvaluatorKey(), version.getConfigSchemaVersion(), version.getConfigJson(),
                    version.getResultSchemaVersion());
            rule.expectedBindings().forEach(expected -> ruleValidator.validateExpected(version.getEvaluatorKey(),
                    JsonUtils.toJson(expected.expectedJson())));
            return Map.<String, Object>of("binding", rule, "evaluatorKey", version.getEvaluatorKey(),
                    "contentHash", version.getContentHash(), "catalogVersion", OnlineProtocolUtils.SCHEMA_VERSION,
                    "expectedHash", OnlineProtocolUtils.hash("online.expected", rule.expectedBindings()));
        }).toList();
    }

    private void requireScope(Long spaceId, List<String> documents) {
        access.requirePermission(spaceId, DOCUMENT_READ);
        var refs = data(documentFeign.getDocumentRefs(documents.stream().map(OnlineExperimentRequestValidator::id).toList()));
        var expected = documents.stream().map(OnlineExperimentRequestValidator::id).collect(Collectors.toSet());
        if (refs.size() != expected.size() || refs.stream().anyMatch(ref -> !spaceId.equals(ref.spaceId())
                || !expected.contains(ref.id())) || refs.stream().map(ref -> ref.id()).distinct().count() != expected.size()) {
            throw conflict(SCOPE_DRIFT.name());
        }
    }

    private OnlineExperimentCreateIntentEntity intent(Long spaceId, Long actor, String key) {
        return intentMapper.selectOne(new LambdaQueryWrapper<OnlineExperimentCreateIntentEntity>()
                .eq(OnlineExperimentCreateIntentEntity::getSpaceId, spaceId)
                .eq(OnlineExperimentCreateIntentEntity::getCreatedBy, actor)
                .eq(OnlineExperimentCreateIntentEntity::getRequestKey, key));
    }

    private void requirePair(AgentOnlineConfigPairVO pair) {
        if (pair.baseline() == null || pair.candidate() == null || pair.executionTimeoutSeconds() == null
                || pair.executionTimeoutSeconds() <= 0 || pair.maxSystemPromptBytes() <= 0
                || pair.baseline().schemaVersion() != OnlineProtocolUtils.SCHEMA_VERSION
                || pair.candidate().schemaVersion() != OnlineProtocolUtils.SCHEMA_VERSION
                || !Objects.equals(pair.baseline().nonPromptHash(), pair.candidate().nonPromptHash())
                || Objects.equals(pair.baseline().id(), pair.candidate().id())
                || Objects.equals(pair.baseline().hash(), pair.candidate().hash())) { throw conflict(TEMPLATE_INVALID.name()); }
        id(pair.baseline().id());
        id(pair.candidate().id());
        for (String hash : new String[] {pair.baseline().hash(), pair.candidate().hash(), pair.baseline().nonPromptHash(), pair.dependencyHash()}) {
            if (hash == null || !hash.matches("[0-9a-f]{64}")) { throw conflict(TEMPLATE_INVALID.name()); }
        }
    }

    private OnlineExperimentVO view(OnlineExperimentEntity entity) {
        boolean supported = Objects.equals(entity.getManifestSchemaVersion(), OnlineProtocolUtils.SCHEMA_VERSION);
        var counts = assignmentMapper.participation(List.of(entity.getId()));
        return convertor.detail(entity, supported ? manifest(entity) : null,
                counts.isEmpty() ? 0L : counts.getFirst().documentCount());
    }

    private OnlineExperimentSummaryVO summary(OnlineExperimentEntity entity, Long participating) {
        boolean supported = Objects.equals(entity.getManifestSchemaVersion(), OnlineProtocolUtils.SCHEMA_VERSION);
        return convertor.summary(entity, supported ? manifest(entity) : null, participating);
    }

    private JsonNode manifest(OnlineExperimentEntity entity) {
        requireSchema(entity);
        try {
            var envelope = OnlineProtocolUtils.object(entity.getManifestJson());
            if (!entity.getManifestHash().equals(StableSnapshotUtils.sha256Utf8(entity.getManifestJson()))
                    || !entity.getManifestJson().equals(OnlineProtocolUtils.canonicalNode(envelope))
                    || !envelope.path("domain").asText().equals("online.manifest")
                    || envelope.path("schemaVersion").asInt() != OnlineProtocolUtils.SCHEMA_VERSION
                    || !envelope.path("payload").path("experimentId").asText().equals(entity.getId().toString())
                    || !envelope.path("payload").path("spaceId").asText().equals(entity.getSpaceId().toString())
                    || !envelope.path("payload").path("agentId").asText().equals(entity.getAgentId().toString())) {
                throw conflict(MANIFEST_INVALID.name());
            }
            return envelope.get("payload");
        } catch (IllegalArgumentException invalid) { throw conflict(MANIFEST_INVALID.name()); }
    }

    private List<String> documents(JsonNode payload) {
        var values = new ArrayList<String>();
        payload.path("documentIds").forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private static String versionHash(EvaluatorVersionEntity version) {
        return StableSnapshotUtils.snapshotHash(1, new VersionHash(version.getEvaluatorKey(), version.getConfigSchemaVersion(),
                JsonUtils.parse(version.getConfigJson(), Object.class), version.getResultSchemaVersion(), version.getImplementationVersion()));
    }

    private void read(Long spaceId) {
        access.requirePermission(spaceId, SPACE_READ);
        access.requirePermission(spaceId, EVALUATION_READ);
    }
    private OnlineExperimentEntity require(Long identity) {
        var value = mapper.selectById(identity);
        if (value == null) { throw new BusinessException(ErrorCode.NOT_FOUND, ONLINE_EXPERIMENT_NOT_FOUND.name()); }
        return value;
    }
    private static void requireSchema(OnlineExperimentEntity entity) {
        if (!Objects.equals(entity.getManifestSchemaVersion(), OnlineProtocolUtils.SCHEMA_VERSION)) { throw conflict(UNSUPPORTED_ONLINE_SCHEMA.name()); }
    }
    private static void same(String left, String right) { if (!Objects.equals(left, right)) { throw conflict(IDEMPOTENCY_CONFLICT.name()); } }
    private static <T> T data(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw conflict(DEPENDENCY_UNAVAILABLE.name());
        }
        return result.data();
    }
    private static OnlineExperimentPreflightVO.Issue issue(String code, String severity) {
        return new OnlineExperimentPreflightVO.Issue(code, severity, null, null);
    }
    private static BusinessException conflict(String code) { return new BusinessException(ErrorCode.CONFLICT, code); }
    private record VersionHash(String evaluatorKey, Integer configSchemaVersion, Object config,
                               Integer resultSchemaVersion, String implementationVersion) { }
}
