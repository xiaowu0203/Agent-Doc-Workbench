package com.agentdoc.agent.service;

import static com.agentdoc.common.enums.OnlineReasonCode.*;

import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.execution.application.ExecutionPreparationTransactionService;
import com.agentdoc.agent.execution.application.AgentExecutionApplicationService;
import com.agentdoc.agent.execution.tool.ExecutionToolSessionFactory;
import com.agentdoc.agent.execution.prompt.PromptService;
import com.agentdoc.agent.execution.skill.SkillSelectionResult;
import com.agentdoc.agent.mapper.AgentOnlineConfigMapper;
import com.agentdoc.agent.pojo.entity.AgentOnlineConfigEntity;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.AgentOnlineConfigPrepareDTO;
import com.agentdoc.common.feign.vo.AgentOnlineConfigPairVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.SnapshotCanonicalV3Utils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HexFormat;
import java.util.Locale;
import java.util.TreeMap;

import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_READ;

/** 当前配置模板独立于历史 CandidateConfig；不新建 Task、不调用模型。 */
@Service
@RequiredArgsConstructor
public class AgentOnlineConfigService {
    private final AgentOnlineConfigMapper mapper;
    private final AgentOnlineConfigPersistenceService persistence;
    private final ExecutionPreparationTransactionService captureService;
    private final SkillSnapshotService skillService;
    private final PromptService promptService;
    private final SkillPackageProperties limits;
    private final SpaceAccessService access;
    private final DocumentFeign documentFeign;

    public AgentOnlineConfigPairVO prepare(AgentOnlineConfigPrepareDTO request) {
        if (request == null) { throw invalid(); }
        long spaceId = id(request.spaceId());
        long experimentId = id(request.experimentId());
        long agentId = id(request.agentId());
        owner(spaceId);
        access.requirePermission(spaceId, EVALUATION_MANAGE);
        access.requirePermission(spaceId, AGENT_MANAGE);
        if (request.requestHash() == null || !request.requestHash().matches("[0-9a-f]{64}")
                || request.candidateAgentPrompt() == null || request.candidateAgentPrompt().isBlank()) { throw invalid(); }
        String pairRequestHash = OnlineProtocolUtils.hash("online.template-request", request);
        var prior = rows(experimentId);
        if (!prior.isEmpty()) { return existing(prior, spaceId, agentId, pairRequestHash); }
        var capture = captureService.capture(agentId);
        if (!Objects.equals(capture.agent().getSpaceId(), spaceId)) { throw invalid(); }
        var material = material(capture);
        String baseline = promptService.systemPrompt(capture.agent().getSystemPrompt(), material.catalog());
        String candidate = promptService.systemPrompt(request.candidateAgentPrompt(), material.catalog());
        long maxBytes = limits.getMaxSystemPromptSize().toBytes();
        if (baseline.equals(candidate) || request.candidateAgentPrompt().getBytes(StandardCharsets.UTF_8).length > maxBytes
                || baseline.getBytes(StandardCharsets.UTF_8).length > maxBytes
                || candidate.getBytes(StandardCharsets.UTF_8).length > maxBytes) { throw invalid(); }
        List<AgentOnlineConfigEntity> pair = List.of(
                template(request, pairRequestHash, "BASELINE", baseline, material, capture.agent().getConfigVersion(), maxBytes),
                template(request, pairRequestHash, "CANDIDATE", candidate, material, capture.agent().getConfigVersion(), maxBytes));
        try { persistence.savePair(pair); }
        catch (DuplicateKeyException race) {
            var concurrent = rows(experimentId);
            if (concurrent.isEmpty()) { throw race; }
            return existing(concurrent, spaceId, agentId, pairRequestHash);
        }
        return existing(pair, spaceId, agentId, pairRequestHash);
    }

    public String dependency(Long experimentId, Long spaceId) {
        owner(spaceId);
        access.requirePermission(spaceId, EVALUATION_READ);
        var pair = rows(experimentId);
        if (pair.size() != 2 || pair.stream().anyMatch(v -> !spaceId.equals(v.getSpaceId()))) { throw invalid(); }
        return material(captureService.capture(pair.getFirst().getAgentId())).dependencyHash();
    }

    private Material material(ExecutionPreparationTransactionService.CapturedExecution capture) {
        var agent = capture.agent();
        var model = capture.model();
        if (!"ALL_BOUND".equals(agent.getSkillSelectionMode()) || Boolean.TRUE.equals(agent.getExternalMcpEnabled())
                || !capture.externalMcpConnections().isEmpty() || agent.getExecutionTimeoutSeconds() == null
                || agent.getExecutionTimeoutSeconds() <= 0) { throw invalid(); }
        var skills = capture.boundSkills().stream().sorted((a, b) -> a.skillVersionId().compareTo(b.skillVersionId())).toList();
        var skillSnapshot = skillService.snapshot(agent, skills, new SkillSelectionResult("ALL_BOUND", skills, null));
        Map<String, Object> nonPrompt = new TreeMap<>();
        nonPrompt.put("modelId", model.getId().toString());
        nonPrompt.put("modelConfigVersion", model.getConfigVersion());
        nonPrompt.put("provider", model.getProvider());
        nonPrompt.put("adapterType", model.getAdapterType());
        nonPrompt.put("modelKey", model.getModelKey());
        nonPrompt.put("serviceHash", StableSnapshotUtils.sha256Utf8(model.getBaseUrl()));
        JsonNode sourceOptions = model.getOptionsJson() == null ? OnlineProtocolUtils.object("{}")
                : JsonUtils.parseStrict(model.getOptionsJson(), JsonNode.class);
        if (sourceOptions == null || !sourceOptions.isObject()) { throw invalid(); }
        JsonNode options = OnlineProtocolUtils.object(SnapshotCanonicalV3Utils.canonicalEnvelope(3, sourceOptions)).get("snapshot");
        rejectSecretFields(options);
        nonPrompt.put("modelOptions", options);
        nonPrompt.put("contextWindow", model.getContextWindow());
        nonPrompt.put("maxOutputTokens", model.getMaxOutputTokens());
        nonPrompt.put("inputPrice", SnapshotCanonicalV3Utils.decimal(model.getInputPricePerMillion()));
        nonPrompt.put("outputPrice", SnapshotCanonicalV3Utils.decimal(model.getOutputPricePerMillion()));
        nonPrompt.put("currency", AgentConstant.TOKEN_PRICING_CURRENCY);
        nonPrompt.put("pricingSchemaVersion", AgentConstant.TOKEN_PRICING_SCHEMA_VERSION);
        nonPrompt.put("docScope", agent.getDocScope());
        nonPrompt.put("maxIterations", agent.getMaxIterations());
        nonPrompt.put("tokenBudget", agent.getTokenBudget());
        nonPrompt.put("executionTimeoutSeconds", agent.getExecutionTimeoutSeconds());
        nonPrompt.put("skills", skills.stream().map(skill -> Map.of(
                "skillId", skill.skillId().toString(), "versionId", skill.skillVersionId().toString(),
                "versionNo", skill.versionNo(), "name", skill.name(),
                "activationDescription", skill.activationDescription(), "packageHash", skill.sha256(),
                "instructionHash", StableSnapshotUtils.sha256Utf8(skill.instructionText()),
                "allowedTools", skill.allowedTools().stream().sorted().toList(),
                "readableResources", skill.readableResources())).toList());
        nonPrompt.put("tools", skillSnapshot.allowedMcpTools() == null ? null :
                skillSnapshot.allowedMcpTools().stream().sorted().toList());
        nonPrompt.put("skillSelectionMode", "ALL_BOUND");
        nonPrompt.put("externalMcpEnabled", false);
        nonPrompt.put("crossTaskSession", false);
        nonPrompt.put("cachePolicy", "TASK_SCOPED");
        nonPrompt.put("runtimeRelease", releaseHash(AgentExecutionApplicationService.class));
        nonPrompt.put("toolRelease", releaseHash(ExecutionToolSessionFactory.class));
        Map<String, Object> dependencies = new TreeMap<>(nonPrompt);
        dependencies.put("agentConfigVersion", agent.getConfigVersion());
        dependencies.put("platformPromptHash", StableSnapshotUtils.sha256Utf8(promptService.systemPrompt("", "")));
        dependencies.put("agentPromptHash", StableSnapshotUtils.sha256Utf8(
                agent.getSystemPrompt() == null ? "" : agent.getSystemPrompt()));
        return new Material(nonPrompt, dependencies, OnlineProtocolUtils.hash("online.dependencies", dependencies),
                skillSnapshot.catalogPromptSection(), agent.getExecutionTimeoutSeconds());
    }

    private AgentOnlineConfigEntity template(AgentOnlineConfigPrepareDTO request, String requestHash, String role,
            String prompt, Material material, Long configVersion, long maxBytes) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("spaceId", request.spaceId());
        payload.put("agentId", request.agentId());
        payload.put("agentConfigVersion", configVersion);
        payload.put("nonPromptConfig", material.nonPrompt());
        payload.put("dependencyManifest", material.dependencies());
        String nonPromptHash = OnlineProtocolUtils.hash("online.non-prompt", payload);
        payload.put("role", role);
        payload.put("systemPrompt", prompt);
        var entity = new AgentOnlineConfigEntity();
        entity.setId(IdWorker.getId());
        entity.setExperimentId(id(request.experimentId()));
        entity.setSpaceId(id(request.spaceId()));
        entity.setAgentId(id(request.agentId()));
        entity.setRole(role);
        entity.setRequestHash(requestHash);
        entity.setSchemaVersion(OnlineProtocolUtils.SCHEMA_VERSION);
        entity.setTemplateJson(OnlineProtocolUtils.canonical("online.template", payload));
        entity.setTemplateHash(OnlineProtocolUtils.hash("online.template", payload));
        entity.setNonPromptHash(nonPromptHash);
        entity.setDependencyHash(material.dependencyHash());
        entity.setExecutionTimeoutSeconds(material.timeout());
        entity.setMaxSystemPromptBytes(maxBytes);
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        return entity;
    }

    private AgentOnlineConfigPairVO existing(List<AgentOnlineConfigEntity> pair, long spaceId, long agentId, String requestHash) {
        if (pair.size() != 2 || pair.stream().anyMatch(v -> !Objects.equals(v.getSpaceId(), spaceId)
                || !Objects.equals(v.getAgentId(), agentId) || !requestHash.equals(v.getRequestHash())
                || !Objects.equals(v.getSchemaVersion(), OnlineProtocolUtils.SCHEMA_VERSION))) { throw invalid(); }
        var baseline = pair.stream().filter(v -> "BASELINE".equals(v.getRole())).findFirst().orElseThrow(AgentOnlineConfigService::invalid);
        var candidate = pair.stream().filter(v -> "CANDIDATE".equals(v.getRole())).findFirst().orElseThrow(AgentOnlineConfigService::invalid);
        for (var value : pair) {
            var envelope = OnlineProtocolUtils.object(value.getTemplateJson());
            if (!value.getTemplateHash().equals(StableSnapshotUtils.sha256Utf8(value.getTemplateJson()))
                    || !OnlineProtocolUtils.canonicalNode(envelope).equals(value.getTemplateJson())
                    || !"online.template".equals(envelope.path("domain").asText())
                    || envelope.path("schemaVersion").asInt() != OnlineProtocolUtils.SCHEMA_VERSION
                    || !envelope.path("payload").path("spaceId").asText().equals(value.getSpaceId().toString())
                    || !envelope.path("payload").path("agentId").asText().equals(value.getAgentId().toString())
                    || !envelope.path("payload").path("role").asText().equals(value.getRole())) { throw invalid(); }
            ObjectNode nonPrompt = ((ObjectNode) envelope.get("payload")).deepCopy();
            nonPrompt.remove(List.of("role", "systemPrompt"));
            if (!value.getNonPromptHash().equals(OnlineProtocolUtils.hash("online.non-prompt", nonPrompt))
                    || !value.getDependencyHash().equals(OnlineProtocolUtils.hash("online.dependencies",
                            nonPrompt.get("dependencyManifest")))) { throw invalid(); }
        }
        if (!baseline.getNonPromptHash().equals(candidate.getNonPromptHash())
                || !baseline.getDependencyHash().equals(candidate.getDependencyHash())) { throw invalid(); }
        return new AgentOnlineConfigPairVO(identity(baseline), identity(candidate), baseline.getDependencyHash(),
                baseline.getExecutionTimeoutSeconds(), baseline.getMaxSystemPromptBytes());
    }

    private AgentOnlineConfigPairVO.TemplateIdentity identity(AgentOnlineConfigEntity entity) {
        return new AgentOnlineConfigPairVO.TemplateIdentity(entity.getId().toString(), entity.getSchemaVersion(),
                entity.getTemplateHash(), entity.getNonPromptHash());
    }

    private List<AgentOnlineConfigEntity> rows(Long experimentId) {
        return mapper.selectList(new LambdaQueryWrapper<AgentOnlineConfigEntity>()
                .eq(AgentOnlineConfigEntity::getExperimentId, experimentId).orderByAsc(AgentOnlineConfigEntity::getRole));
    }

    private void owner(Long spaceId) {
        Result<Void> result = documentFeign.checkSpaceOwner(spaceId);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, OWNER_REQUIRED.name());
        }
    }

    private static String releaseHash(Class<?> type) {
        try (var input = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            if (input == null) { throw invalid(); }
            return StableSnapshotUtils.sha256Utf8(HexFormat.of().formatHex(input.readAllBytes()));
        } catch (IOException failure) { throw new BusinessException(ErrorCode.INTERNAL_ERROR, DEPENDENCY_UNAVAILABLE.name()); }
    }

    private static void rejectSecretFields(JsonNode value) {
        if (value == null) { throw invalid(); }
        if (value.isObject()) { value.fields().forEachRemaining(field -> {
            String key = field.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (key.matches(".*(secret|password|credential|authorization|apikey).*|token|accesstoken|refreshtoken|bearertoken|authtoken")) {
                throw invalid();
            }
            rejectSecretFields(field.getValue());
        }); } else if (value.isArray()) { value.forEach(AgentOnlineConfigService::rejectSecretFields); }
    }

    private static long id(String value) {
        try { return OnlineProtocolUtils.id(value); }
        catch (IllegalArgumentException invalid) { throw invalid(); }
    }

    private static BusinessException invalid() { return new BusinessException(ErrorCode.CONFLICT, TEMPLATE_INVALID.name()); }

    private record Material(Map<String, Object> nonPrompt, Map<String, Object> dependencies,
                            String dependencyHash, String catalog, int timeout) { }
}
