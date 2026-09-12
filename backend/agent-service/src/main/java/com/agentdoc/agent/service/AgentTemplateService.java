package com.agentdoc.agent.service;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.enums.SkillSelectionMode;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.AgentTemplateMapper;
import com.agentdoc.agent.mapper.AgentTemplateMcpMapper;
import com.agentdoc.agent.mapper.AgentTemplateSkillMapper;
import com.agentdoc.agent.mapper.AgentTemplateVersionMapper;
import com.agentdoc.agent.pojo.dto.AgentCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentMcpBindingItemDTO;
import com.agentdoc.agent.pojo.dto.AgentMcpBindingReplaceDTO;
import com.agentdoc.agent.pojo.dto.AgentSkillReplaceDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateInstallDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateMcpDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateSkillDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateUpdateDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateUpgradeDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateVersionCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentUpdateDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentTemplateEntity;
import com.agentdoc.agent.pojo.entity.AgentTemplateMcpEntity;
import com.agentdoc.agent.pojo.entity.AgentTemplateSkillEntity;
import com.agentdoc.agent.pojo.entity.AgentTemplateVersionEntity;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.entity.McpTemplateVersionEntity;
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

/**
 * 系统 Agent 模板、不可变版本及空间安装服务。
 *
 * <p>
 * 管理平台侧Agent模板与多版本生命周期：模板为主体，模板版本为快照；版本草稿创建、发布后不可修改。
 * 模板版本可绑定系统Skill与MCP模板引用，发布后支持在空间内一键安装生成Agent实例，
 * 同时提供已安装Agent基于模板版本的升级能力，并处理升级时的配置冲突合并逻辑。
 * 权限约束：模板与版本的创建/更新/发布仅平台超管操作；空间安装、Agent升级由空间管理员操作。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentTemplateService {

    private final AgentTemplateMapper templateMapper;
    private final AgentTemplateVersionMapper versionMapper;
    private final AgentTemplateSkillMapper templateSkillMapper;
    private final AgentTemplateMcpMapper templateMcpMapper;
    private final SpaceSkillInstallationService skillInstallationService;
    private final PlatformAccessService platformAccessService;
    private final ModelService modelService;
    private final AgentService agentService;
    private final AgentSkillService agentSkillService;
    private final AgentMcpBindingService agentMcpBindingService;
    private final McpServerService mcpServerService;
    private final McpTemplateService mcpTemplateService;
    private final SpaceAccessService spaceAccessService;
    private final SkillAuditLogService auditLogService;

    /**
     * 创建Agent模板主体（无版本，版本需要单独createVersion生成）。
     *
     * @param dto 模板基础信息入参
     * @return 模板VO
     */
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

    /**
     * 分页查询Agent模板列表。
     *
     * <p>权限过滤：超管可见全部模板；普通用户仅可见【已启用模板+存在已发布版本】的模板。</p>
     *
     * @param param 分页+搜索参数
     * @return 分页VO，附带模板最新发布版本号
     */
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
        // 批量查询每个模板最新已发布版本
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

    /**
     * 更新Agent模板主体基础信息（模板本体，不影响已存在的版本快照）。
     *
     * @param templateId 模板ID
     * @param dto 更新入参：展示名称、描述、启用状态
     * @return 更新后模板VO
     */
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

    /**
     * 创建模板草稿版本快照，绑定Skill与MCP引用；版本号自动递增。
     *
     * <p>版本创建后默认为DRAFT草稿状态，草稿可发布；发布后版本不可修改。
     * 事务内锁定模板，递增NextVersionNo保证版本号连续不重复。</p>
     *
     * @param templateId 所属模板ID
     * @param dto 版本配置：模型、提示词、Skill/MCP引用、迭代限制等
     * @return 新建草稿版本VO
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentTemplateVersionVO createVersion(Long templateId, AgentTemplateVersionCreateDTO dto) {
        requireManage();
        // 行锁锁定模板，防止并发创建版本造成版本号冲突
        AgentTemplateEntity template = templateMapper.selectOne(new LambdaQueryWrapper<AgentTemplateEntity>()
                .eq(AgentTemplateEntity::getId, templateId).last("FOR UPDATE"));
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 模板不存在");
        }
        validateModel(dto.modelId(), dto.skillRouterModelId(), dto.skillSelectionMode().name());
        validateSkillReferences(dto.skills(), false);
        Map<Long, McpTemplateVersionEntity> mcpVersions = validateMcpReferences(dto.mcps(), false);
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
        // 持久化模板版本绑定的Skill引用
        for (AgentTemplateSkillDTO skill : safeSkills(dto.skills())) {
            AgentTemplateSkillEntity reference = new AgentTemplateSkillEntity();
            reference.setTemplateVersionId(version.getId());
            reference.setSkillId(skill.skillId());
            reference.setSkillVersionId(skill.skillVersionId());
            templateSkillMapper.insert(reference);
        }
        // 持久化模板版本绑定的MCP模板引用
        for (AgentTemplateMcpDTO mcp : safeMcps(dto.mcps())) {
            McpTemplateVersionEntity mcpVersion = mcpVersions.get(mcp.mcpTemplateVersionId());
            AgentTemplateMcpEntity reference = new AgentTemplateMcpEntity();
            reference.setTemplateVersionId(version.getId());
            reference.setMcpTemplateId(mcpVersion.getTemplateId());
            reference.setMcpTemplateVersionId(mcpVersion.getId());
            reference.setToolWhitelistJson(mcp.toolWhitelist() == null ? null
                    : JsonUtils.toJson(mcp.toolWhitelist().stream().distinct().sorted().toList()));
            templateMcpMapper.insert(reference);
        }
        // 模板版本号自增，供下一次创建版本使用
        template.setNextVersionNo(template.getNextVersionNo() + 1);
        templateMapper.updateById(template);
        auditLogService.record(null, "AGENT_TEMPLATE_VERSION_CREATED", "agent_template_version", version.getId(),
                Map.of("templateId", templateId, "versionNo", version.getVersionNo()));
        return toVersionVO(version);
    }

    /**
     * 发布草稿模板版本，状态变更为PUBLISHED。
     *
     * <p>发布前校验模型、Skill、MCP引用合法性；发布后的版本不可修改，可用于空间安装。</p>
     *
     * @param versionId 草稿版本ID
     * @return 发布后版本VO
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentTemplateVersionVO publish(Long versionId) {
        requireManage();
        AgentTemplateVersionEntity version = requireVersion(versionId);
        if (!SkillVersionStatus.DRAFT.matches(version.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有草稿模板版本可以发布");
        }
        validateModel(version.getModelId(), version.getSkillRouterModelId(), version.getSkillSelectionMode());
        validateStoredSkillReferences(versionId, true);
        validateStoredMcpReferences(versionId, true);
        version.setStatus(SkillVersionStatus.PUBLISHED.getCode());
        version.setPublishedBy(AuthUtils.getUserIdOrException());
        version.setPublishedAt(LocalDateTime.now());
        versionMapper.updateById(version);
        auditLogService.record(null, "AGENT_TEMPLATE_VERSION_PUBLISHED", "agent_template_version", version.getId(),
                Map.of("templateId", version.getTemplateId(), "versionNo", version.getVersionNo()));
        return toVersionVO(version);
    }

    /**
     * 查询模板下全部版本列表。
     *
     * <p>权限过滤：普通用户仅可查看已发布版本；超管可查看草稿+已发布。</p>
     *
     * @param templateId 模板ID
     * @return 版本VO列表，附带绑定的Skill、MCP引用
     */
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
        // 批量查询版本关联的Skill、MCP引用，避免N+1
        Map<Long, List<AgentTemplateSkillEntity>> skillsByVersion = templateSkillMapper.selectList(
                        new LambdaQueryWrapper<AgentTemplateSkillEntity>()
                                .in(AgentTemplateSkillEntity::getTemplateVersionId,
                                        versions.stream().map(AgentTemplateVersionEntity::getId).toList()))
                .stream().collect(Collectors.groupingBy(AgentTemplateSkillEntity::getTemplateVersionId));
        Map<Long, List<AgentTemplateMcpEntity>> mcpsByVersion = templateMcpMapper.selectList(
                        new LambdaQueryWrapper<AgentTemplateMcpEntity>()
                                .in(AgentTemplateMcpEntity::getTemplateVersionId,
                                        versions.stream().map(AgentTemplateVersionEntity::getId).toList()))
                .stream().collect(Collectors.groupingBy(AgentTemplateMcpEntity::getTemplateVersionId));
        return versions.stream().map(version -> toVersionVO(version,
                skillsByVersion.getOrDefault(version.getId(), List.of()),
                mcpsByVersion.getOrDefault(version.getId(), List.of()))).toList();
    }

    /**
     * 在指定空间安装已发布模板版本，生成空间Agent实例，并绑定模板版本快照。
     *
     * <p>安装逻辑：
     * 1. 校验模板启用、版本已发布；
     * 2. 使用模板版本配置创建Agent基础实例；
     * 3. 将模板版本内Skill/MCP引用绑定到Agent；
     * 4. 标记Agent来源templateId、templateVersionId，标记为模板实例。
     * </p>
     *
     * @param spaceId 目标空间ID
     * @param dto 安装参数（模板版本ID、Agent自定义名称、文档范围）
     * @return 新建Agent详情VO
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentVO install(Long spaceId, AgentTemplateInstallDTO dto) {
        AgentTemplateVersionEntity version = requireVersion(dto.templateVersionId());
        AgentTemplateEntity template = requireTemplate(version.getTemplateId());
        if (!AgentStatus.ENABLED.matches(template.getStatus())
                || !SkillVersionStatus.PUBLISHED.matches(version.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只能安装已启用模板的已发布版本");
        }
        validateStoredSkillReferences(version.getId(), false);
        validateStoredMcpReferences(version.getId(), false);
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
        // 绑定模板版本定义的Skill
        List<Long> skillVersionIds = listSkillReferences(version.getId()).stream()
                .map(AgentTemplateSkillEntity::getSkillVersionId).toList();
        if (!skillVersionIds.isEmpty()) {
            agentSkillService.replace(agent.getId(), new AgentSkillReplaceDTO(skillVersionIds));
        }
        // 解析MCP引用并绑定到Agent
        List<AgentMcpBindingItemDTO> mcpBindings = resolveMcpBindings(spaceId, version.getId());
        if (!mcpBindings.isEmpty()) {
            agentMcpBindingService.replace(agent.getId(), new AgentMcpBindingReplaceDTO(mcpBindings));
        }
        auditLogService.record(spaceId, "AGENT_TEMPLATE_INSTALLED", "agent", agent.getId(),
                Map.of("templateId", template.getId(), "templateVersionId", version.getId()));
        return agentService.detail(agent.getId());
    }

    /**
     * 已安装模板Agent升级到同模板的更高版本，支持预览模式与冲突合并。
     *
     * <p>升级核心逻辑：
     * 1. 校验目标版本：同模板、已发布、版本号大于当前；
     * 2. 对比【旧模板版本默认值】、【Agent当前用户修改配置】、【新版本默认值】做三向合并；
     * 3. 存在冲突字段时，预览模式仅返回冲突列表；正式升级需要传入用户确认后的resolved配置；
     * 4. 更新Agent配置、替换Skill/MCP绑定，更新Agent关联的templateVersionId并触发配置版本递增。
     * </p>
     *
     * @param agentId 待升级AgentID
     * @param dto 升级参数：目标版本ID、是否仅预览、用户确认后的冲突解决配置
     * @return 升级结果VO（含冲突列表、合并后配置，预览标记）
     */
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
        validateStoredMcpReferences(targetVersion.getId(), false);

        List<String> conflicts = new ArrayList<>();
        AgentUpdateDTO current = fromAgent(agent);
        AgentUpdateDTO oldDefaults = fromVersion(currentVersion, agent.getDocScope(), agent.getStatus());
        AgentUpdateDTO newDefaults = fromVersion(targetVersion, agent.getDocScope(), agent.getStatus());
        AgentUpdateDTO proposed = mergeConfig(oldDefaults, current, newDefaults, conflicts);
        List<Long> oldSkills = skillVersionIds(currentVersion.getId());
        List<Long> currentSkills = agentSkillService.listEnabledVersionIds(agentId);
        List<Long> newSkills = skillVersionIds(targetVersion.getId());
        List<Long> proposedSkills = mergeValue(oldSkills, currentSkills, newSkills, "skills", conflicts);
        List<AgentMcpBindingItemDTO> oldMcps = resolveMcpBindings(agent.getSpaceId(), currentVersion.getId());
        List<AgentMcpBindingItemDTO> currentMcps = agentMcpBindingService.listEnabledItems(agentId);
        List<AgentMcpBindingItemDTO> newMcps = resolveMcpBindings(agent.getSpaceId(), targetVersion.getId());
        List<AgentMcpBindingItemDTO> proposedMcps = mergeValue(oldMcps, currentMcps, newMcps, "mcps", conflicts);

        // 预览模式：只计算冲突与推荐配置，不执行数据库修改
        if (dto.previewOnly()) {
            return new AgentTemplateUpgradeVO(agentId, currentVersion.getId(), targetVersion.getId(),
                    List.copyOf(conflicts), proposed, proposedSkills, proposedMcps, false);
        }
        AgentUpdateDTO finalConfig = proposed;
        List<Long> finalSkills = proposedSkills;
        List<AgentMcpBindingItemDTO> finalMcps = proposedMcps;
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
        if (conflicts.contains("mcps")) {
            if (dto.resolvedMcpBindings() == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "模板升级存在 MCP 冲突，请确认最终绑定");
            }
            finalMcps = normalizeMcpBindings(dto.resolvedMcpBindings());
        }
        agentService.update(agentId, finalConfig);
        agentSkillService.replace(agentId, new AgentSkillReplaceDTO(finalSkills));
        agentMcpBindingService.replace(agentId, new AgentMcpBindingReplaceDTO(finalMcps));
        AgentEntity updated = agentService.requireForUpdate(agentId);
        updated.setTemplateVersionId(targetVersion.getId());
        agentService.updateConfiguration(updated);
        auditLogService.record(agent.getSpaceId(), "AGENT_TEMPLATE_UPGRADED", "agent", agentId,
                Map.of("fromVersionId", currentVersion.getId(), "toVersionId", targetVersion.getId(),
                        "resolvedConflicts", conflicts));
        return new AgentTemplateUpgradeVO(agentId, currentVersion.getId(), targetVersion.getId(),
                List.copyOf(conflicts), finalConfig, finalSkills, finalMcps, true);
    }

    /**
     * 三向合并Agent基础配置：旧模板默认值 / Agent当前配置 / 新版本默认值。
     *
     * <p>合并规则：用户未修改的字段（当前==旧默认）直接升级为新版本默认；
     * 用户已修改字段，新版本默认发生变更则标记冲突，保留用户当前值。</p>
     *
     * @param oldValue 旧模板版本默认配置
     * @param current Agent当前生效配置
     * @param target 新版本模板默认配置
     * @param conflicts 输出：冲突字段名列表
     * @return 合并后的推荐AgentUpdateDTO
     */
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

    /**
     * 泛型三向合并单个字段，升级冲突判断核心方法。
     *
     * <p>规则：
     * 1. current == oldValue：用户没有修改，直接使用新版本target值；
     * 2. target == oldValue 或 current == target：无冲突，保留用户当前值；
     * 3. 其他场景：用户修改过，新版本默认又变了 → 标记冲突，保留用户当前值。
     * </p>
     *
     * @param oldValue 旧版本默认值
     * @param current Agent当前值
     * @param target 新版本默认值
     * @param field 字段名称，冲突时记录
     * @param conflicts 冲突列表
     * @return 合并后推荐值
     */
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

    /**
     * 将Agent实体转换为AgentUpdateDTO，用于升级配置对比。
     *
     * @param agent Agent实体
     * @return AgentUpdateDTO
     */
    private AgentUpdateDTO fromAgent(AgentEntity agent) {
        return new AgentUpdateDTO(agent.getName(), agent.getDescription(), agent.getSystemPrompt(), agent.getModelId(),
                SkillSelectionMode.valueOf(agent.getSkillSelectionMode()),
                agent.getSkillRouterModelId(), agent.getExternalMcpEnabled(), agent.getTokenBudget(),
                agent.getDocScope(), parseTools(agent.getToolWhitelist()), agent.getMaxIterations(),
                agent.getExecutionTimeoutSeconds(), agent.getStatus());
    }

    /**
     * 将模板版本快照转换为AgentUpdateDTO（模板默认配置）。
     *
     * @param version 模板版本实体
     * @param documentScope 文档范围（不属于模板版本，取自Agent）
     * @param status Agent状态
     * @return AgentUpdateDTO
     */
    private AgentUpdateDTO fromVersion(AgentTemplateVersionEntity version, String documentScope, Integer status) {
        return new AgentUpdateDTO(version.getDisplayName(), version.getDescription(), version.getSystemPrompt(),
                version.getModelId(), SkillSelectionMode.valueOf(version.getSkillSelectionMode()),
                version.getSkillRouterModelId(), version.getExternalMcpEnabled(), version.getTokenBudget(), documentScope,
                parseTools(version.getToolWhitelist()), version.getMaxIterations(), version.getExecutionTimeoutSeconds(),
                status);
    }

    /**
     * 获取模板版本绑定的全部Skill版本ID列表，去重排序。
     *
     * @param templateVersionId 模板版本ID
     * @return skillVersionId列表
     */
    private List<Long> skillVersionIds(Long templateVersionId) {
        return listSkillReferences(templateVersionId).stream().map(AgentTemplateSkillEntity::getSkillVersionId)
                .distinct().sorted().toList();
    }

    /**
     * 校验模型配置合法性，校验主模型启用状态，校验路由模型与选择模式互斥约束。
     *
     * @param modelId 主模型ID
     * @param routerModelId 路由模型ID
     * @param selectionMode Skill选择模式名称
     */
    private void validateModel(Long modelId, Long routerModelId, String selectionMode) {
        modelService.requireEnabled(modelId);
        if ("ALL_BOUND".equals(selectionMode) && routerModelId != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "ALL_BOUND 模式不能配置 Router 模型");
        }
        if ("ROUTER".equals(selectionMode) && routerModelId != null && !routerModelId.equals(modelId)) {
            modelService.requireEnabled(routerModelId);
        }
    }

    /**
     * 读取数据库中已保存的Skill引用，再做引用合法性校验。
     *
     * @param templateVersionId 模板版本ID
     * @param requireActive 是否强制校验系统Skill主状态启用
     */
    private void validateStoredSkillReferences(Long templateVersionId, boolean requireActive) {
        List<AgentTemplateSkillEntity> references = listSkillReferences(templateVersionId);
        validateSkillReferences(references.stream()
                        .map(value -> new AgentTemplateSkillDTO(value.getSkillId(), value.getSkillVersionId())).toList(),
                requireActive);
    }

    /**
     * 读取数据库中已保存的MCP引用，再做引用合法性校验。
     *
     * @param templateVersionId 模板版本ID
     * @param requireActive 是否强制校验MCP模板启用
     */
    private void validateStoredMcpReferences(Long templateVersionId, boolean requireActive) {
        validateMcpReferences(listMcpReferences(templateVersionId).stream()
                .map(value -> new AgentTemplateMcpDTO(value.getMcpTemplateVersionId(),
                        parseTools(value.getToolWhitelistJson()))).toList(), requireActive);
    }

    /**
     * 校验MCP模板引用：不能重复引用同一个MCP模板，调用McpTemplateService校验版本合法性。
     *
     * @param references MCP模板引用DTO列表
     * @param requireActive 是否强制MCP模板启用
     */
    private Map<Long, McpTemplateVersionEntity> validateMcpReferences(List<AgentTemplateMcpDTO> references,
                                                                       boolean requireActive) {
        Map<Long, McpTemplateVersionEntity> versions = mcpTemplateService.requirePublishedVersions(
                safeMcps(references).stream().map(AgentTemplateMcpDTO::mcpTemplateVersionId).toList(), requireActive);
        Set<Long> templateIds = new HashSet<>();
        for (AgentTemplateMcpDTO reference : safeMcps(references)) {
            if (!templateIds.add(versions.get(reference.mcpTemplateVersionId()).getTemplateId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "模板不能重复引用同一系统 MCP 模板");
            }
        }
        return versions;
    }

    /**
     * 根据模板版本MCP引用，解析当前空间对应的McpServer实例绑定关系。
     *
     * <p>前置约束：空间必须预先安装模板要求的MCP模板指定版本，否则抛出异常。</p>
     *
     * @param spaceId 目标空间
     * @param templateVersionId 模板版本ID
     * @return AgentMcpBindingItemDTO绑定列表
     */
    private List<AgentMcpBindingItemDTO> resolveMcpBindings(Long spaceId, Long templateVersionId) {
        List<AgentTemplateMcpEntity> references = listMcpReferences(templateVersionId);
        if (references.isEmpty()) {
            return List.of();
        }
        Map<Long, McpServerEntity> installedByTemplate = mcpServerService.findTemplateInstallations(spaceId,
                        references.stream().map(AgentTemplateMcpEntity::getMcpTemplateId).toList())
                .stream().collect(Collectors.toMap(McpServerEntity::getTemplateId, Function.identity(),
                        (left, right) -> left));
        List<AgentMcpBindingItemDTO> bindings = new ArrayList<>();
        for (AgentTemplateMcpEntity reference : references) {
            McpServerEntity server = installedByTemplate.get(reference.getMcpTemplateId());
            if (server == null || !reference.getMcpTemplateVersionId().equals(server.getTemplateVersionId())) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "请先在当前空间安装 Agent 模板要求的 MCP 模板版本");
            }
            bindings.add(new AgentMcpBindingItemDTO(server.getId(),
                    parseTools(reference.getToolWhitelistJson())));
        }
        return normalizeMcpBindings(bindings);
    }

    /**
     * MCP绑定归一化：工具白名单去重排序，整体按mcpServerId排序，保证对比一致性。
     *
     * @param bindings MCP绑定列表
     * @return 归一化后的绑定列表
     */
    private List<AgentMcpBindingItemDTO> normalizeMcpBindings(List<AgentMcpBindingItemDTO> bindings) {
        return bindings.stream().map(value -> new AgentMcpBindingItemDTO(value.mcpServerId(),
                        value.toolWhitelist() == null ? null
                                : value.toolWhitelist().stream().distinct().sorted().toList()))
                .sorted(java.util.Comparator.comparing(AgentMcpBindingItemDTO::mcpServerId)).toList();
    }

    /**
     * 校验Skill引用：同一模板版本不可重复引用同一个系统Skill；
     * 调用SpaceSkillInstallationService批量校验系统Skill+版本合法性。
     *
     * @param references Skill引用DTO列表
     * @param requireActive 是否强制系统Skill启用
     */
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

    /**
     * 根据ID查询模板实体，不存在抛NOT_FOUND。
     *
     * @param id templateId
     * @return AgentTemplateEntity
     */
    private AgentTemplateEntity requireTemplate(Long id) {
        AgentTemplateEntity entity = templateMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 模板不存在");
        }
        return entity;
    }

    /**
     * 根据ID查询模板版本实体，不存在抛NOT_FOUND。
     *
     * @param id templateVersionId
     * @return AgentTemplateVersionEntity
     */
    private AgentTemplateVersionEntity requireVersion(Long id) {
        AgentTemplateVersionEntity entity = versionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 模板版本不存在");
        }
        return entity;
    }

    /**
     * 查询模板版本下所有Skill引用记录。
     *
     * @param versionId 模板版本ID
     * @return AgentTemplateSkillEntity列表
     */
    private List<AgentTemplateSkillEntity> listSkillReferences(Long versionId) {
        return templateSkillMapper.selectList(new LambdaQueryWrapper<AgentTemplateSkillEntity>()
                .eq(AgentTemplateSkillEntity::getTemplateVersionId, versionId));
    }

    /**
     * 查询模板版本下所有MCP引用记录。
     *
     * @param versionId 模板版本ID
     * @return AgentTemplateMcpEntity列表
     */
    private List<AgentTemplateMcpEntity> listMcpReferences(Long versionId) {
        return templateMcpMapper.selectList(new LambdaQueryWrapper<AgentTemplateMcpEntity>()
                .eq(AgentTemplateMcpEntity::getTemplateVersionId, versionId));
    }

    /**
     * 转换模板实体到VO，自动查询最新发布版本号。
     *
     * @param entity 模板实体
     * @return AgentTemplateVO
     */
    private AgentTemplateVO toVO(AgentTemplateEntity entity) {
        AgentTemplateVersionEntity latest = versionMapper.selectOne(
                new LambdaQueryWrapper<AgentTemplateVersionEntity>()
                        .eq(AgentTemplateVersionEntity::getTemplateId, entity.getId())
                        .eq(AgentTemplateVersionEntity::getStatus, SkillVersionStatus.PUBLISHED.getCode())
                        .orderByDesc(AgentTemplateVersionEntity::getVersionNo).last("LIMIT 1"));
        return toVO(entity, latest == null ? null : latest.getVersionNo());
    }

    /**
     * 转换模板实体到VO，传入已知最新版本号。
     *
     * @param entity 模板实体
     * @param latestPublishedVersionNo 最新发布版本号（可为null）
     * @return AgentTemplateVO
     */
    private AgentTemplateVO toVO(AgentTemplateEntity entity, Integer latestPublishedVersionNo) {
        return new AgentTemplateVO(entity.getId(), entity.getName(), entity.getDisplayName(), entity.getDescription(),
                entity.getStatus(), latestPublishedVersionNo, entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /**
     * 转换模板版本实体到VO，自动查询Skill/MCP引用。
     *
     * @param version 模板版本实体
     * @return AgentTemplateVersionVO
     */
    private AgentTemplateVersionVO toVersionVO(AgentTemplateVersionEntity version) {
        return toVersionVO(version, listSkillReferences(version.getId()), listMcpReferences(version.getId()));
    }

    /**
     * 转换模板版本实体+预加载的Skill/MCP引用到VO。
     *
     * @param version 模板版本实体
     * @param skillReferences 预加载Skill引用列表
     * @param mcpReferences 预加载MCP引用列表
     * @return AgentTemplateVersionVO
     */
    private AgentTemplateVersionVO toVersionVO(AgentTemplateVersionEntity version,
                                               List<AgentTemplateSkillEntity> skillReferences,
                                               List<AgentTemplateMcpEntity> mcpReferences) {
        List<AgentTemplateVersionVO.SkillReferenceVO> skills = skillReferences.stream()
                .map(value -> new AgentTemplateVersionVO.SkillReferenceVO(
                        value.getSkillId(), value.getSkillVersionId())).toList();
        List<AgentTemplateVersionVO.McpReferenceVO> mcps = mcpReferences.stream()
                .map(value -> new AgentTemplateVersionVO.McpReferenceVO(value.getMcpTemplateId(),
                        value.getMcpTemplateVersionId(), parseTools(value.getToolWhitelistJson()))).toList();
        return new AgentTemplateVersionVO(version.getId(), version.getTemplateId(), version.getVersionNo(),
                version.getStatus(), version.getDisplayName(), version.getDescription(), version.getSystemPrompt(),
                version.getModelId(), SkillSelectionMode.valueOf(version.getSkillSelectionMode()),
                version.getSkillRouterModelId(), version.getExternalMcpEnabled(), version.getTokenBudget(),
                parseTools(version.getToolWhitelist()), version.getMaxIterations(), version.getExecutionTimeoutSeconds(),
                skills, mcps, version.getCreatedBy(), version.getPublishedBy(), version.getPublishedAt(),
                version.getCreatedAt());
    }

    /**
     * 解析JSON字符串为工具白名单List<String>。
     *
     * @param json 序列化后的工具列表JSON
     * @return 工具名称列表
     */
    private List<String> parseTools(String json) {
        return json == null ? null : JsonUtils.parse(json, new TypeReference<List<String>>() { });
    }

    /**
     * 空安全包装Skill列表，null转为空集合。
     *
     * @param skills 原始dto.skills
     * @return 非空List
     */
    private List<AgentTemplateSkillDTO> safeSkills(List<AgentTemplateSkillDTO> skills) {
        return skills == null ? List.of() : skills;
    }

    /**
     * 空安全包装MCP列表，null转为空集合。
     *
     * @param mcps 原始dto.mcps
     * @return 非空List
     */
    private List<AgentTemplateMcpDTO> safeMcps(List<AgentTemplateMcpDTO> mcps) {
        return mcps == null ? List.of() : mcps;
    }

    /**
     * 权限校验：平台超管角色，用于模板/版本的新建、修改、发布。
     */
    private void requireManage() {
        platformAccessService.requireRole(SUPER_ADMIN);
    }
}
