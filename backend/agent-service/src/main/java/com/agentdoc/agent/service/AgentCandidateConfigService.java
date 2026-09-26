package com.agentdoc.agent.service;

import com.agentdoc.agent.config.SkillPackageProperties;
import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.execution.application.ExecutionPreparationTransactionService;
import com.agentdoc.agent.execution.prompt.PromptService;
import com.agentdoc.agent.execution.skill.SkillCandidate;
import com.agentdoc.agent.mapper.AgentCandidateConfigMapper;
import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.pojo.entity.AgentCandidateConfigEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentCandidateConfigCreateDTO;
import com.agentdoc.common.feign.vo.AgentCandidateConfigVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.SnapshotCanonicalV3Utils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_MANAGE;

/**
 * Prompt 候选配置冻结与恢复服务。
 * <p>候选配置只能从来源 execution snapshot v3 派生，且唯一允许变化的字段为
 * {@code snapshot.systemPrompt}。普通跨服务契约只返回引用、哈希和差异证明。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentCandidateConfigService {

    private static final int REQUEST_SCHEMA_VERSION = 1;
    private static final String SYSTEM_PROMPT_PATH = "snapshot.systemPrompt";
    private static final List<String> PROMPT_DIFF_PATHS = List.of(SYSTEM_PROMPT_PATH);

    private final AgentCandidateConfigMapper candidateConfigMapper;
    private final AgentExecutionMapper executionMapper;
    private final SpaceAccessService spaceAccessService;
    private final SkillSnapshotService skillSnapshotService;
    private final PromptService promptService;
    private final SkillPackageProperties skillPackageProperties;
    private final ExecutionPreparationTransactionService preparationTransactionService;

    /** 创建或幂等返回不可变 Prompt 候选配置。 */
    public AgentCandidateConfigVO create(AgentCandidateConfigCreateDTO request) {
        requireCreateRequest(request);
        spaceAccessService.requirePermission(request.spaceId(), AGENT_MANAGE);
        Long createdBy = AuthUtils.getUserIdOrException();
        String requestHash = requestHash(request);
        AgentCandidateConfigEntity existing = findByRequestKey(request.requestKey());
        if (existing != null) {
            requireSameRequest(existing, requestHash);
            validateProof(existing, false);
            return toVO(existing);
        }

        AgentExecutionEntity source = requireSource(request.sourceExecutionId(), request.spaceId(),
                request.sourceTaskId(), request.sourceSnapshotSchemaVersion(), request.sourceSnapshotHash());
        JsonNode sourceSnapshot = requireCanonicalSnapshot(source.getExecutionSnapshotJson(),
                request.sourceSnapshotHash(), "来源");
        requireSourceSnapshotBindings(source, sourceSnapshot);
        requireExternalMcpAbsent(sourceSnapshot);

        String catalogSection = skillSnapshotService.catalogPromptSection(
                selectedSkills(sourceSnapshot));
        String systemPrompt = promptService.systemPrompt(request.agentPrompt(), catalogSection);
        if (systemPrompt.getBytes(StandardCharsets.UTF_8).length
                > skillPackageProperties.getMaxSystemPromptSize().toBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "候选系统提示词超过限制");
        }
        String sourcePrompt = text(sourceSnapshot, "systemPrompt");
        if (Objects.equals(sourcePrompt, systemPrompt)) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选 Prompt 未产生变化");
        }

        ObjectNode candidateSnapshot = ((ObjectNode) sourceSnapshot).deepCopy();
        candidateSnapshot.put("systemPrompt", systemPrompt);
        String candidateJson = SnapshotCanonicalV3Utils.canonicalEnvelope(
                AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION, candidateSnapshot);
        String candidateHash = SnapshotCanonicalV3Utils.hashEnvelope(candidateJson);
        String withoutPromptHash = requireOnlyPromptChanged(sourceSnapshot, candidateSnapshot);

        AgentCandidateConfigEntity entity = new AgentCandidateConfigEntity();
        entity.setSpaceId(request.spaceId());
        entity.setAgentId(source.getAgentId());
        entity.setSourceTaskId(request.sourceTaskId());
        entity.setSourceExecutionId(request.sourceExecutionId());
        entity.setRequestKey(request.requestKey());
        entity.setRequestHash(requestHash);
        entity.setSourceSnapshotSchemaVersion(request.sourceSnapshotSchemaVersion());
        entity.setSourceSnapshotHash(request.sourceSnapshotHash());
        entity.setCandidateSnapshotSchemaVersion(AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION);
        entity.setCandidateSnapshotHash(candidateHash);
        entity.setSnapshotWithoutPromptHash(withoutPromptHash);
        entity.setPromptDiffFieldPaths(JsonUtils.toJson(PROMPT_DIFF_PATHS));
        entity.setAgentPrompt(request.agentPrompt());
        entity.setSystemPrompt(systemPrompt);
        entity.setPromptHash(promptService.hash(systemPrompt, source.getUserInstructionSnapshot()));
        entity.setExecutionSnapshotJson(candidateJson);
        entity.setCreatedBy(createdBy);
        try {
            candidateConfigMapper.insert(entity);
            return toVO(entity);
        } catch (DuplicateKeyException exception) {
            AgentCandidateConfigEntity concurrent = findByRequestKey(request.requestKey());
            if (concurrent == null) {
                throw exception;
            }
            requireSameRequest(concurrent, requestHash);
            validateProof(concurrent, false);
            return toVO(concurrent);
        }
    }

    /**
     * 恢复运行时所需的冻结候选配置，并重新校验来源、完整性、空间和当前模型凭证。
     * 不通过普通接口暴露该返回对象。
     */
    public RestoredCandidateConfig restore(Long candidateConfigId, Long spaceId,
                                            String candidateSnapshotHash) {
        AgentCandidateConfigEntity entity = requireCandidateIdentity(
                candidateConfigId, spaceId, candidateSnapshotHash);
        JsonNode candidateSnapshot = validateProof(entity, true);
        return new RestoredCandidateConfig(entity.getId(), entity.getSpaceId(), entity.getAgentId(),
                entity.getSourceTaskId(), entity.getSourceExecutionId(),
                entity.getSourceSnapshotHash(),
                entity.getCandidateSnapshotSchemaVersion(), entity.getCandidateSnapshotHash(),
                entity.getSystemPrompt(), entity.getPromptHash(), entity.getExecutionSnapshotJson(),
                candidateSnapshot.deepCopy());
    }

    /** 恢复校验候选配置后，仅返回非敏感身份投影。 */
    public AgentCandidateConfigVO requireRestorableIdentity(Long candidateConfigId, Long spaceId,
                                                            String candidateSnapshotHash) {
        AgentCandidateConfigEntity entity = requireCandidateIdentity(
                candidateConfigId, spaceId, candidateSnapshotHash);
        validateProof(entity, true);
        return toVO(entity);
    }

    private AgentCandidateConfigEntity requireCandidateIdentity(Long candidateConfigId, Long spaceId,
                                                                String candidateSnapshotHash) {
        AgentCandidateConfigEntity entity = candidateConfigMapper.selectById(candidateConfigId);
        if (entity == null || !Objects.equals(spaceId, entity.getSpaceId())
                || !Objects.equals(candidateSnapshotHash, entity.getCandidateSnapshotHash())) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选配置身份无效");
        }
        return entity;
    }

    private JsonNode validateProof(AgentCandidateConfigEntity entity, boolean requireCredential) {
        if (entity.getSourceSnapshotSchemaVersion() == null
                || entity.getSourceSnapshotSchemaVersion() != AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION
                || entity.getCandidateSnapshotSchemaVersion() == null
                || entity.getCandidateSnapshotSchemaVersion() != AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION
                || !validHash(entity.getSourceSnapshotHash()) || !validHash(entity.getCandidateSnapshotHash())
                || !validHash(entity.getSnapshotWithoutPromptHash()) || !validHash(entity.getRequestHash())) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选配置证明无效");
        }
        AgentExecutionEntity source = requireSource(entity.getSourceExecutionId(), entity.getSpaceId(),
                entity.getSourceTaskId(), entity.getSourceSnapshotSchemaVersion(), entity.getSourceSnapshotHash());
        if (!Objects.equals(source.getAgentId(), entity.getAgentId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选配置来源 Agent 无效");
        }
        JsonNode sourceSnapshot = requireCanonicalSnapshot(source.getExecutionSnapshotJson(),
                entity.getSourceSnapshotHash(), "来源");
        requireSourceSnapshotBindings(source, sourceSnapshot);
        requireExternalMcpAbsent(sourceSnapshot);
        JsonNode candidateSnapshot = requireCanonicalSnapshot(entity.getExecutionSnapshotJson(),
                entity.getCandidateSnapshotHash(), "候选");
        String withoutPromptHash = requireOnlyPromptChanged(sourceSnapshot, candidateSnapshot);
        List<String> paths = JsonUtils.parse(entity.getPromptDiffFieldPaths(),
                new TypeReference<List<String>>() { });
        if (!PROMPT_DIFF_PATHS.equals(paths)
                || !Objects.equals(withoutPromptHash, entity.getSnapshotWithoutPromptHash())
                || !Objects.equals(text(candidateSnapshot, "systemPrompt"), entity.getSystemPrompt())
                || !Objects.equals(promptService.hash(entity.getSystemPrompt(), source.getUserInstructionSnapshot()),
                entity.getPromptHash())
                || !Objects.equals(requestHash(new AgentCandidateConfigCreateDTO(entity.getRequestKey(),
                entity.getSpaceId(), entity.getSourceTaskId(), entity.getSourceExecutionId(),
                entity.getSourceSnapshotSchemaVersion(), entity.getSourceSnapshotHash(), entity.getAgentPrompt())),
                entity.getRequestHash())) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选配置证明无效");
        }
        if (requireCredential) {
            Long modelId = longValue(candidateSnapshot.path("model").get("id"));
            if (modelId == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "候选配置缺少模型快照");
            }
            ModelEntity model = preparationTransactionService.resolveReplayModelCredential(modelId);
            if (model == null || model.getEncryptedApiKey() == null || model.getEncryptedApiKey().isBlank()) {
                throw new BusinessException(ErrorCode.CONFLICT, "候选配置模型凭证不可用");
            }
        }
        return candidateSnapshot;
    }

    private AgentExecutionEntity requireSource(Long executionId, Long spaceId, Long taskId,
                                                Integer schemaVersion, String snapshotHash) {
        AgentExecutionEntity source = executionMapper.selectById(executionId);
        if (source == null || !Objects.equals(spaceId, source.getSpaceId())
                || !Objects.equals(taskId, source.getWorkbenchTaskId())
                || !Objects.equals(schemaVersion, source.getExecutionSnapshotSchemaVersion())
                || !Objects.equals(snapshotHash, source.getExecutionSnapshotHash())
                || source.getUserInstructionSnapshot() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "来源执行身份无效");
        }
        return source;
    }

    private JsonNode requireCanonicalSnapshot(String json, String expectedHash, String label) {
        JsonNode envelope = JsonUtils.parse(json, JsonNode.class);
        JsonNode snapshot = envelope == null ? null : envelope.get("snapshot");
        JsonNode schema = envelope == null ? null : envelope.get("schemaVersion");
        if (envelope == null || !envelope.isObject() || envelope.size() != 2
                || schema == null || !schema.canConvertToInt()
                || schema.intValue() != AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION
                || snapshot == null || !snapshot.isObject()) {
            throw new BusinessException(ErrorCode.CONFLICT, label + "执行快照无法解析");
        }
        String canonical = SnapshotCanonicalV3Utils.canonicalEnvelope(
                AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION, snapshot);
        if (!Objects.equals(canonical, json)
                || !Objects.equals(SnapshotCanonicalV3Utils.hashEnvelope(canonical), expectedHash)) {
            throw new BusinessException(ErrorCode.CONFLICT, label + "执行快照完整性校验失败");
        }
        return snapshot;
    }

    private void requireSourceSnapshotBindings(AgentExecutionEntity source, JsonNode snapshot) {
        if (!Objects.equals(longValue(snapshot.get("sourceAgentId")), source.getAgentId())
                || !Objects.equals(text(snapshot, "systemPrompt"), source.getSystemPromptSnapshot())) {
            throw new BusinessException(ErrorCode.CONFLICT, "来源执行快照绑定无效");
        }
    }

    private void requireExternalMcpAbsent(JsonNode snapshot) {
        JsonNode externalMcp = snapshot.get("externalMcpSnapshot");
        if (externalMcp != null && !externalMcp.isNull()
                && (!externalMcp.isArray() || !externalMcp.isEmpty())) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选配置不允许外部 MCP");
        }
    }

    private List<SkillCandidate> selectedSkills(JsonNode snapshot) {
        JsonNode skillsNode = snapshot.get("skillSnapshot");
        JsonNode selectedNode = snapshot.get("selectedSkillVersionIds");
        List<SkillCandidate> skills = skillsNode == null || skillsNode.isNull() ? List.of()
                : JsonUtils.parse(skillsNode.toString(), new TypeReference<List<SkillCandidate>>() { });
        List<Long> selected = selectedNode == null || selectedNode.isNull() ? List.of()
                : JsonUtils.parse(selectedNode.toString(), new TypeReference<List<Long>>() { });
        if (skills == null || selected == null || selected.stream().anyMatch(Objects::isNull)
                || selected.stream().distinct().count() != selected.size()) {
            throw new BusinessException(ErrorCode.CONFLICT, "来源 Skill 快照无效");
        }
        List<SkillCandidate> result = skills.stream()
                .filter(skill -> selected.contains(skill.skillVersionId())).toList();
        if (result.size() != selected.size()) {
            throw new BusinessException(ErrorCode.CONFLICT, "来源 Skill 快照无效");
        }
        return result;
    }

    private String requireOnlyPromptChanged(JsonNode source, JsonNode candidate) {
        ObjectNode sourceWithoutPrompt = ((ObjectNode) source).deepCopy();
        ObjectNode candidateWithoutPrompt = ((ObjectNode) candidate).deepCopy();
        JsonNode sourcePrompt = sourceWithoutPrompt.remove("systemPrompt");
        JsonNode candidatePrompt = candidateWithoutPrompt.remove("systemPrompt");
        String sourceCanonical = SnapshotCanonicalV3Utils.canonicalEnvelope(
                AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION, sourceWithoutPrompt);
        String candidateCanonical = SnapshotCanonicalV3Utils.canonicalEnvelope(
                AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION, candidateWithoutPrompt);
        if (sourcePrompt == null || candidatePrompt == null || Objects.equals(sourcePrompt, candidatePrompt)
                || !Objects.equals(sourceCanonical, candidateCanonical)) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选执行快照存在非 Prompt 差异");
        }
        return SnapshotCanonicalV3Utils.hashEnvelope(sourceCanonical);
    }

    private void requireCreateRequest(AgentCandidateConfigCreateDTO request) {
        if (request == null || request.requestKey() == null || request.requestKey().isBlank()
                || request.requestKey().length() > 191 || request.spaceId() == null
                || request.sourceTaskId() == null || request.sourceExecutionId() == null
                || request.sourceSnapshotSchemaVersion() == null
                || request.sourceSnapshotSchemaVersion() != AgentConstant.EXECUTION_SNAPSHOT_SCHEMA_VERSION
                || !validHash(request.sourceSnapshotHash()) || request.agentPrompt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "候选配置创建参数不完整");
        }
    }

    private boolean validHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private String requestHash(AgentCandidateConfigCreateDTO request) {
        return StableSnapshotUtils.snapshotHash(REQUEST_SCHEMA_VERSION, new CandidateRequestSnapshot(
                request.requestKey(), request.spaceId(), request.sourceTaskId(), request.sourceExecutionId(),
                request.sourceSnapshotSchemaVersion(), request.sourceSnapshotHash(), request.agentPrompt()));
    }

    private AgentCandidateConfigEntity findByRequestKey(String requestKey) {
        return candidateConfigMapper.selectOne(new LambdaQueryWrapper<AgentCandidateConfigEntity>()
                .eq(AgentCandidateConfigEntity::getRequestKey, requestKey));
    }

    private void requireSameRequest(AgentCandidateConfigEntity entity, String requestHash) {
        if (!Objects.equals(entity.getRequestHash(), requestHash)) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选配置幂等键已被不同请求使用");
        }
    }

    private AgentCandidateConfigVO toVO(AgentCandidateConfigEntity entity) {
        List<String> paths = JsonUtils.parse(entity.getPromptDiffFieldPaths(),
                new TypeReference<List<String>>() { });
        if (paths == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选配置证明无效");
        }
        return new AgentCandidateConfigVO(entity.getId(), entity.getSpaceId(), entity.getAgentId(),
                entity.getSourceTaskId(), entity.getSourceExecutionId(), entity.getSourceSnapshotSchemaVersion(),
                entity.getSourceSnapshotHash(), entity.getCandidateSnapshotSchemaVersion(),
                entity.getCandidateSnapshotHash(), entity.getSnapshotWithoutPromptHash(), paths);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long longValue(JsonNode value) {
        return value == null || value.isNull() || !value.canConvertToLong() ? null : value.longValue();
    }

    private record CandidateRequestSnapshot(String requestKey, Long spaceId, Long sourceTaskId,
                                            Long sourceExecutionId, Integer sourceSnapshotSchemaVersion,
                                            String sourceSnapshotHash, String agentPrompt) {
    }

    /** Agent Runtime 内部使用的已校验冻结候选配置。 */
    public record RestoredCandidateConfig(Long candidateConfigId, Long spaceId, Long agentId,
                                          Long sourceTaskId, Long sourceExecutionId,
                                          String sourceSnapshotHash,
                                          Integer candidateSnapshotSchemaVersion,
                                          String candidateSnapshotHash, String systemPrompt,
                                          String promptHash, String executionSnapshotJson,
                                          JsonNode snapshot) {
        public RestoredCandidateConfig {
            snapshot = snapshot.deepCopy();
        }

        @Override
        public JsonNode snapshot() {
            return snapshot.deepCopy();
        }
    }
}
