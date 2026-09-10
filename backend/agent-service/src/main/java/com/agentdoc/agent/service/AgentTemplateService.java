package com.agentdoc.agent.service;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.enums.SkillSelectionMode;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.AgentTemplateMapper;
import com.agentdoc.agent.mapper.AgentTemplateSkillMapper;
import com.agentdoc.agent.mapper.AgentTemplateVersionMapper;
import com.agentdoc.agent.pojo.dto.AgentCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentSkillReplaceDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateInstallDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateSkillDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateUpdateDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateUpgradeDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateVersionCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentUpdateDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentTemplateEntity;
import com.agentdoc.agent.pojo.entity.AgentTemplateSkillEntity;
import com.agentdoc.agent.pojo.entity.AgentTemplateVersionEntity;
import com.agentdoc.agent.pojo.param.AgentTemplateSearchParam;
import com.agentdoc.agent.pojo.vo.AgentTemplateVO;
import com.agentdoc.agent.pojo.vo.AgentTemplateVersionVO;
import com.agentdoc.agent.pojo.vo.AgentTemplateUpgradeVO;
import com.agentdoc.agent.pojo.vo.AgentVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.PageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_MANAGE;

/** 系统 Agent 模板、不可变版本及空间安装服务。 */
@Service
@RequiredArgsConstructor
public class AgentTemplateService {

    private final AgentTemplateMapper templateMapper;
    private final AgentTemplateVersionMapper versionMapper;
    private final AgentTemplateSkillMapper templateSkillMapper;
    private final SpaceSkillInstallationService skillInstallationService;
    private final PlatformAccessService platformAccessService;
    private final ModelService modelService;
    private final AgentService agentService;
    private final AgentSkillService agentSkillService;
    private final SpaceAccessService spaceAccessService;
    private final SkillAuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public AgentTemplateVO create(AgentTemplateCreateDTO dto) {
        requireManage();
        AgentTemplateEntity entity = new AgentTemplateEntity();
        entity.setName(dto.name());
        entity.setDisplayName(dto.displayName());
        entity.setDescription(dto.description());
        entity.setStatus(AgentStatus.ENABLED.getCode());
        entity.setNextVersionNo(1);
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        templateMapper.insert(entity);
        auditLogService.record(null, "AGENT_TEMPLATE_CREATED", "agent_template", entity.getId(),
                Map.of("name", entity.getName()));
        return toVO(entity);
    }

    public PageVO<AgentTemplateVO> search(AgentTemplateSearchParam param) {
        param.validate();
        boolean manager = platformAccessService.hasRole(SUPER_ADMIN);
        LambdaQueryWrapper<AgentTemplateEntity> query = new LambdaQueryWrapper<AgentTemplateEntity>()
                .orderByDesc(AgentTemplateEntity::getUpdatedAt).orderByDesc(AgentTemplateEntity::getId);
        if (!manager) {
            query.eq(AgentTemplateEntity::getStatus, AgentStatus.ENABLED.getCode());
            query.inSql(AgentTemplateEntity::getId,
                    "SELECT DISTINCT template_id FROM agent_template_version WHERE status = "
                            + SkillVersionStatus.PUBLISHED.getCode());
        } else if (param.getStatus() != null) {
            query.eq(AgentTemplateEntity::getStatus, param.getStatus());
        }
        if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
            String keyword = param.getKeyword().trim();
            query.and(value -> value.like(AgentTemplateEntity::getDisplayName, keyword)
                    .or().like(AgentTemplateEntity::getName, keyword)
                    .or().like(AgentTemplateEntity::getDescription, keyword));
        }
        Page<AgentTemplateEntity> page = templateMapper.selectPage(PageUtils.toPage(param), query);
        if (page.getRecords().isEmpty()) {
            return PageVO.of(List.of(), page.getTotal(), param);
        }
        Set<Long> templateIds = page.getRecords().stream().map(AgentTemplateEntity::getId)
                .collect(Collectors.toSet());
        Map<Long, AgentTemplateVersionEntity> latestByTemplate = versionMapper.selectList(
                        new LambdaQueryWrapper<AgentTemplateVersionEntity>()
                                .in(AgentTemplateVersionEntity::getTemplateId, templateIds)
                                .eq(AgentTemplateVersionEntity::getStatus, SkillVersionStatus.PUBLISHED.getCode()))
                .stream().collect(Collectors.toMap(AgentTemplateVersionEntity::getTemplateId, Function.identity(),
                        (left, right) -> left.getVersionNo() >= right.getVersionNo() ? left : right));
        return PageVO.of(page.getRecords().stream().map(entity -> {
            AgentTemplateVersionEntity latest = latestByTemplate.get(entity.getId());
            return toVO(entity, latest == null ? null : latest.getVersionNo());
        }).toList(), page.getTotal(), param);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTemplateVO update(Long templateId, AgentTemplateUpdateDTO dto) {
        requireManage();
        AgentTemplateEntity entity = requireTemplate(templateId);
        entity.setDisplayName(dto.displayName());
        entity.setDescription(dto.description());
        entity.setStatus(AgentStatus.fromCode(dto.status()).getCode());
        templateMapper.updateById(entity);
        auditLogService.record(null, "AGENT_TEMPLATE_UPDATED", "agent_template", entity.getId(),
                Map.of("status", entity.getStatus()));
        return toVO(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTemplateVersionVO createVersion(Long templateId, AgentTemplateVersionCreateDTO dto) {
        requireManage();
        AgentTemplateEntity template = templateMapper.selectOne(new LambdaQueryWrapper<AgentTemplateEntity>()
                .eq(AgentTemplateEntity::getId, templateId).last("FOR UPDATE"));
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 模板不存在");
        }
        validateModel(dto.modelId(), dto.skillRouterModelId(), dto.skillSelectionMode().name());
        validateSkillReferences(dto.skills(), false);
        AgentTemplateVersionEntity version = new AgentTemplateVersionEntity();
        version.setTemplateId(templateId);
        version.setVersionNo(template.getNextVersionNo());
        version.setStatus(SkillVersionStatus.DRAFT.getCode());
        version.setDisplayName(dto.displayName());
        version.setDescription(dto.description());
        version.setSystemPrompt(dto.systemPrompt());
        version.setModelId(dto.modelId());
        version.setSkillSelectionMode(dto.skillSelectionMode().name());
        version.setSkillRouterModelId(dto.skillRouterModelId());
        version.setExternalMcpEnabled(dto.externalMcpEnabled());
        version.setTokenBudget(dto.tokenBudget());
        version.setToolWhitelist(dto.toolWhitelist() == null ? null
                : JsonUtils.toJson(dto.toolWhitelist().stream().distinct().sorted().toList()));
        version.setMaxIterations(dto.maxIterations() == null
                ? AgentConstant.DEFAULT_MAX_ITERATIONS : dto.maxIterations());
        version.setExecutionTimeoutSeconds(dto.executionTimeoutSeconds() == null
                ? AgentConstant.DEFAULT_EXECUTION_TIMEOUT_SECONDS : dto.executionTimeoutSeconds());
        version.setCreatedBy(AuthUtils.getUserIdOrException());
        versionMapper.insert(version);
        for (AgentTemplateSkillDTO skill : safeSkills(dto.skills())) {
            AgentTemplateSkillEntity reference = new AgentTemplateSkillEntity();
            reference.setTemplateVersionId(version.getId());
            reference.setSkillId(skill.skillId());
            reference.setSkillVersionId(skill.skillVersionId());
            templateSkillMapper.insert(reference);
        }
        template.setNextVersionNo(template.getNextVersionNo() + 1);
        templateMapper.updateById(template);
        auditLogService.record(null, "AGENT_TEMPLATE_VERSION_CREATED", "agent_template_version", version.getId(),
                Map.of("templateId", templateId, "versionNo", version.getVersionNo()));
        return toVersionVO(version);
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTemplateVersionVO publish(Long versionId) {
        requireManage();
        AgentTemplateVersionEntity version = requireVersion(versionId);
        if (!SkillVersionStatus.DRAFT.matches(version.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有草稿模板版本可以发布");
        }
        validateModel(version.getModelId(), version.getSkillRouterModelId(), version.getSkillSelectionMode());
        validateStoredSkillReferences(versionId, true);
        version.setStatus(SkillVersionStatus.PUBLISHED.getCode());
        version.setPublishedBy(AuthUtils.getUserIdOrException());
        version.setPublishedAt(LocalDateTime.now());
        versionMapper.updateById(version);
        auditLogService.record(null, "AGENT_TEMPLATE_VERSION_PUBLISHED", "agent_template_version", version.getId(),
                Map.of("templateId", version.getTemplateId(), "versionNo", version.getVersionNo()));
        return toVersionVO(version);
    }

    public List<AgentTemplateVersionVO> listVersions(Long templateId) {
        AgentTemplateEntity template = requireTemplate(templateId);
        boolean manager = platformAccessService.hasRole(SUPER_ADMIN);
        if (!manager && !AgentStatus.ENABLED.matches(template.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 模板不存在");
        }
        LambdaQueryWrapper<AgentTemplateVersionEntity> query = new LambdaQueryWrapper<AgentTemplateVersionEntity>()
                .eq(AgentTemplateVersionEntity::getTemplateId, templateId)
                .orderByDesc(AgentTemplateVersionEntity::getVersionNo);
        if (!manager) {
            query.eq(AgentTemplateVersionEntity::getStatus, SkillVersionStatus.PUBLISHED.getCode());
        }
        List<AgentTemplateVersionEntity> versions = versionMapper.selectList(query);
        if (versions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AgentTemplateSkillEntity>> skillsByVersion = templateSkillMapper.selectList(
                        new LambdaQueryWrapper<AgentTemplateSkillEntity>()
                                .in(AgentTemplateSkillEntity::getTemplateVersionId,
                                        versions.stream().map(AgentTemplateVersionEntity::getId).toList()))
                .stream().collect(Collectors.groupingBy(AgentTemplateSkillEntity::getTemplateVersionId));
        return versions.stream().map(version -> toVersionVO(version,
                skillsByVersion.getOrDefault(version.getId(), List.of()))).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentVO install(Long spaceId, AgentTemplateInstallDTO dto) {
        AgentTemplateVersionEntity version = requireVersion(dto.templateVersionId());
        AgentTemplateEntity template = requireTemplate(version.getTemplateId());
        if (!AgentStatus.ENABLED.matches(template.getStatus())
                || !SkillVersionStatus.PUBLISHED.matches(version.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能安装已启用模板的已发布版本");
        }
        validateStoredSkillReferences(version.getId(), false);
        AgentCreateDTO create = new AgentCreateDTO(spaceId,
                dto.name() == null || dto.name().isBlank() ? version.getDisplayName() : dto.name().trim(),
                version.getDescription(), version.getSystemPrompt(), version.getModelId(),
                SkillSelectionMode.valueOf(version.getSkillSelectionMode()),
                version.getSkillRouterModelId(), version.getExternalMcpEnabled(), version.getTokenBudget(),
                dto.documentScope(), parseTools(version.getToolWhitelist()), version.getMaxIterations(),
                version.getExecutionTimeoutSeconds());
        AgentVO created = agentService.create(create);
        AgentEntity agent = agentService.requireForUpdate(created.id());
        agent.setTemplateId(template.getId());
        agent.setTemplateVersionId(version.getId());
        agentService.updateConfiguration(agent);
        List<Long> skillVersionIds = listSkillReferences(version.getId()).stream()
                .map(AgentTemplateSkillEntity::getSkillVersionId).toList();
        if (!skillVersionIds.isEmpty()) {
            agentSkillService.replace(agent.getId(), new AgentSkillReplaceDTO(skillVersionIds));
        }
        auditLogService.record(spaceId, "AGENT_TEMPLATE_INSTALLED", "agent", agent.getId(),
                Map.of("templateId", template.getId(), "templateVersionId", version.getId()));
        return agentService.detail(agent.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTemplateUpgradeVO upgrade(Long agentId, AgentTemplateUpgradeDTO dto) {
        AgentEntity agent = agentService.require(agentId);
        spaceAccessService.requirePermission(agent.getSpaceId(), AGENT_MANAGE);
        if (agent.getTemplateId() == null || agent.getTemplateVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "空间自定义 Agent 没有可升级的来源模板");
        }
        AgentTemplateEntity template = requireTemplate(agent.getTemplateId());
        AgentTemplateVersionEntity currentVersion = requireVersion(agent.getTemplateVersionId());
        AgentTemplateVersionEntity targetVersion = requireVersion(dto.targetVersionId());
        if (!AgentStatus.ENABLED.matches(template.getStatus())
                || !template.getId().equals(targetVersion.getTemplateId())
                || !SkillVersionStatus.PUBLISHED.matches(targetVersion.getStatus())
                || targetVersion.getVersionNo() <= currentVersion.getVersionNo()) {
            throw new BusinessException(ErrorCode.CONFLICT, "目标版本不是当前模板的可用新版本");
        }
        validateStoredSkillReferences(targetVersion.getId(), false);

        List<String> conflicts = new ArrayList<>();
        AgentUpdateDTO current = fromAgent(agent);
        AgentUpdateDTO oldDefaults = fromVersion(currentVersion, agent.getDocScope(), agent.getStatus());
        AgentUpdateDTO newDefaults = fromVersion(targetVersion, agent.getDocScope(), agent.getStatus());
        AgentUpdateDTO proposed = mergeConfig(oldDefaults, current, newDefaults, conflicts);
        List<Long> oldSkills = skillVersionIds(currentVersion.getId());
        List<Long> currentSkills = agentSkillService.listEnabledVersionIds(agentId);
        List<Long> newSkills = skillVersionIds(targetVersion.getId());
        List<Long> proposedSkills = mergeValue(oldSkills, currentSkills, newSkills, "skills", conflicts);

        if (dto.previewOnly()) {
            return new AgentTemplateUpgradeVO(agentId, currentVersion.getId(), targetVersion.getId(),
                    List.copyOf(conflicts), proposed, proposedSkills, false);
        }
        AgentUpdateDTO finalConfig = proposed;
        List<Long> finalSkills = proposedSkills;
        boolean configConflict = conflicts.stream().anyMatch(field -> !"skills".equals(field));
        if (configConflict) {
            if (dto.resolvedConfig() == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "模板升级存在配置冲突，请确认最终配置");
            }
            finalConfig = dto.resolvedConfig();
        }
        if (conflicts.contains("skills")) {
            if (dto.resolvedSkillVersionIds() == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "模板升级存在 Skill 冲突，请确认最终绑定");
            }
            finalSkills = dto.resolvedSkillVersionIds().stream().distinct().sorted().toList();
        }
        agentService.update(agentId, finalConfig);
        agentSkillService.replace(agentId, new AgentSkillReplaceDTO(finalSkills));
        AgentEntity updated = agentService.requireForUpdate(agentId);
        updated.setTemplateVersionId(targetVersion.getId());
        agentService.updateConfiguration(updated);
        auditLogService.record(agent.getSpaceId(), "AGENT_TEMPLATE_UPGRADED", "agent", agentId,
                Map.of("fromVersionId", currentVersion.getId(), "toVersionId", targetVersion.getId(),
                        "resolvedConflicts", conflicts));
        return new AgentTemplateUpgradeVO(agentId, currentVersion.getId(), targetVersion.getId(),
                List.copyOf(conflicts), finalConfig, finalSkills, true);
    }

    private AgentUpdateDTO mergeConfig(AgentUpdateDTO oldValue, AgentUpdateDTO current,
                                       AgentUpdateDTO target, List<String> conflicts) {
        return new AgentUpdateDTO(
                mergeValue(oldValue.name(), current.name(), target.name(), "name", conflicts),
                mergeValue(oldValue.description(), current.description(), target.description(), "description", conflicts),
                mergeValue(oldValue.systemPrompt(), current.systemPrompt(), target.systemPrompt(), "systemPrompt", conflicts),
                mergeValue(oldValue.modelId(), current.modelId(), target.modelId(), "modelId", conflicts),
                mergeValue(oldValue.skillSelectionMode(), current.skillSelectionMode(), target.skillSelectionMode(),
                        "skillSelectionMode", conflicts),
                mergeValue(oldValue.skillRouterModelId(), current.skillRouterModelId(), target.skillRouterModelId(),
                        "skillRouterModelId", conflicts),
                mergeValue(oldValue.externalMcpEnabled(), current.externalMcpEnabled(), target.externalMcpEnabled(),
                        "externalMcpEnabled", conflicts),
                mergeValue(oldValue.tokenBudget(), current.tokenBudget(), target.tokenBudget(), "tokenBudget", conflicts),
                current.documentScope(),
                mergeValue(oldValue.toolWhitelist(), current.toolWhitelist(), target.toolWhitelist(),
                        "toolWhitelist", conflicts),
                mergeValue(oldValue.maxIterations(), current.maxIterations(), target.maxIterations(),
                        "maxIterations", conflicts),
                mergeValue(oldValue.executionTimeoutSeconds(), current.executionTimeoutSeconds(),
                        target.executionTimeoutSeconds(), "executionTimeoutSeconds", conflicts),
                current.status());
    }

    private <T> T mergeValue(T oldValue, T current, T target, String field, List<String> conflicts) {
        if (Objects.equals(current, oldValue)) {
            return target;
        }
        if (Objects.equals(target, oldValue) || Objects.equals(current, target)) {
            return current;
        }
        conflicts.add(field);
        return current;
    }

    private AgentUpdateDTO fromAgent(AgentEntity agent) {
        return new AgentUpdateDTO(agent.getName(), agent.getDescription(), agent.getSystemPrompt(), agent.getModelId(),
                SkillSelectionMode.valueOf(agent.getSkillSelectionMode()),
                agent.getSkillRouterModelId(), agent.getExternalMcpEnabled(), agent.getTokenBudget(),
                agent.getDocScope(), parseTools(agent.getToolWhitelist()), agent.getMaxIterations(),
                agent.getExecutionTimeoutSeconds(), agent.getStatus());
    }

    private AgentUpdateDTO fromVersion(AgentTemplateVersionEntity version, String documentScope, Integer status) {
        return new AgentUpdateDTO(version.getDisplayName(), version.getDescription(), version.getSystemPrompt(),
                version.getModelId(), SkillSelectionMode.valueOf(version.getSkillSelectionMode()),
                version.getSkillRouterModelId(), version.getExternalMcpEnabled(), version.getTokenBudget(), documentScope,
                parseTools(version.getToolWhitelist()), version.getMaxIterations(), version.getExecutionTimeoutSeconds(),
                status);
    }

    private List<Long> skillVersionIds(Long templateVersionId) {
        return listSkillReferences(templateVersionId).stream().map(AgentTemplateSkillEntity::getSkillVersionId)
                .distinct().sorted().toList();
    }

    private void validateModel(Long modelId, Long routerModelId, String selectionMode) {
        modelService.requireEnabled(modelId);
        if ("ALL_BOUND".equals(selectionMode) && routerModelId != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ALL_BOUND 模式不能配置 Router 模型");
        }
        if ("ROUTER".equals(selectionMode) && routerModelId != null && !routerModelId.equals(modelId)) {
            modelService.requireEnabled(routerModelId);
        }
    }

    private void validateStoredSkillReferences(Long templateVersionId, boolean requireActive) {
        List<AgentTemplateSkillEntity> references = listSkillReferences(templateVersionId);
        validateSkillReferences(references.stream()
                .map(value -> new AgentTemplateSkillDTO(value.getSkillId(), value.getSkillVersionId())).toList(),
                requireActive);
    }

    private void validateSkillReferences(List<AgentTemplateSkillDTO> references, boolean requireActive) {
        List<AgentTemplateSkillDTO> values = safeSkills(references);
        if (values.isEmpty()) {
            return;
        }
        Set<Long> skillIds = new HashSet<>();
        for (AgentTemplateSkillDTO reference : values) {
            if (!skillIds.add(reference.skillId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模板不能重复引用同一系统 Skill");
            }
        }
        skillInstallationService.requirePublishedSystemVersions(values.stream().collect(Collectors.toMap(
                AgentTemplateSkillDTO::skillId, AgentTemplateSkillDTO::skillVersionId)), requireActive);
    }

    private AgentTemplateEntity requireTemplate(Long id) {
        AgentTemplateEntity entity = templateMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 模板不存在");
        }
        return entity;
    }

    private AgentTemplateVersionEntity requireVersion(Long id) {
        AgentTemplateVersionEntity entity = versionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 模板版本不存在");
        }
        return entity;
    }

    private List<AgentTemplateSkillEntity> listSkillReferences(Long versionId) {
        return templateSkillMapper.selectList(new LambdaQueryWrapper<AgentTemplateSkillEntity>()
                .eq(AgentTemplateSkillEntity::getTemplateVersionId, versionId));
    }

    private AgentTemplateVO toVO(AgentTemplateEntity entity) {
        AgentTemplateVersionEntity latest = versionMapper.selectOne(
                new LambdaQueryWrapper<AgentTemplateVersionEntity>()
                        .eq(AgentTemplateVersionEntity::getTemplateId, entity.getId())
                        .eq(AgentTemplateVersionEntity::getStatus, SkillVersionStatus.PUBLISHED.getCode())
                        .orderByDesc(AgentTemplateVersionEntity::getVersionNo).last("LIMIT 1"));
        return toVO(entity, latest == null ? null : latest.getVersionNo());
    }

    private AgentTemplateVO toVO(AgentTemplateEntity entity, Integer latestPublishedVersionNo) {
        return new AgentTemplateVO(entity.getId(), entity.getName(), entity.getDisplayName(), entity.getDescription(),
                entity.getStatus(), latestPublishedVersionNo, entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AgentTemplateVersionVO toVersionVO(AgentTemplateVersionEntity version) {
        return toVersionVO(version, listSkillReferences(version.getId()));
    }

    private AgentTemplateVersionVO toVersionVO(AgentTemplateVersionEntity version,
                                                List<AgentTemplateSkillEntity> references) {
        List<AgentTemplateVersionVO.SkillReferenceVO> skills = references.stream()
                .map(value -> new AgentTemplateVersionVO.SkillReferenceVO(
                        value.getSkillId(), value.getSkillVersionId())).toList();
        return new AgentTemplateVersionVO(version.getId(), version.getTemplateId(), version.getVersionNo(),
                version.getStatus(), version.getDisplayName(), version.getDescription(), version.getSystemPrompt(),
                version.getModelId(), SkillSelectionMode.valueOf(version.getSkillSelectionMode()),
                version.getSkillRouterModelId(), version.getExternalMcpEnabled(), version.getTokenBudget(),
                parseTools(version.getToolWhitelist()), version.getMaxIterations(), version.getExecutionTimeoutSeconds(),
                skills, version.getCreatedBy(), version.getPublishedBy(), version.getPublishedAt(),
                version.getCreatedAt());
    }

    private List<String> parseTools(String json) {
        return json == null ? null : JsonUtils.parse(json, new TypeReference<List<String>>() { });
    }

    private List<AgentTemplateSkillDTO> safeSkills(List<AgentTemplateSkillDTO> skills) {
        return skills == null ? List.of() : skills;
    }

    private void requireManage() {
        platformAccessService.requireRole(SUPER_ADMIN);
    }
}
