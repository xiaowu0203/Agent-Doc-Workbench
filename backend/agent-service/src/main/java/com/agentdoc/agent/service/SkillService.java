package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.SkillConvertor;
import com.agentdoc.agent.convertor.SkillVersionConvertor;
import com.agentdoc.agent.enums.SkillScopeType;
import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.dto.SkillCreateDTO;
import com.agentdoc.agent.pojo.dto.SkillUpdateDTO;
import com.agentdoc.agent.pojo.dto.SystemSkillCreateDTO;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.param.SkillSearchParam;
import com.agentdoc.agent.pojo.param.SystemSkillSearchParam;
import com.agentdoc.agent.pojo.vo.SkillBindingCountVO;
import com.agentdoc.agent.pojo.vo.SkillLatestVersionVO;
import com.agentdoc.agent.pojo.vo.SkillVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.PageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_READ;

/**
 * Skill 主实体服务。
 *
 * <p>
 * 负责 Skill 主表的创建、查询、更新、启停、版本号预留和作用域校验。
 * Skill 版本包上传、发布、下载和对象存储由 {@link SkillVersionService} 负责。
 * </p>
 *
 * <p>
 * SYSTEM Skill 与 SPACE Skill 共用 {@link SkillEntity}，通过 scopeType 和 spaceId 区分：
 * </p>
 *
 * <ul>
 *     <li>SYSTEM：scopeType=SYSTEM，spaceId=null，由平台管理员维护。</li>
 *     <li>SPACE：scopeType=SPACE，spaceId 不为空，由空间权限控制。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    /**
     * Skill 技术名称格式：kebab-case。
     * 小写字母、数字，短横线分隔，不能以横杠开头结尾。
     */
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final SkillMapper skillMapper;
    private final SkillVersionMapper skillVersionMapper;
    private final SpaceAccessService spaceAccessService;
    private final PlatformAccessService platformAccessService;
    private final SkillAuditLogService auditLogService;

    /**
     * 创建空间 Skill。
     * <p>空间内普通技能，归属某个租户空间，受空间权限管控。</p>
     *
     * @param dto 创建参数DTO
     * @return 新建Skill主实体
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillEntity create(SkillCreateDTO dto) {
        return createInternal(
                SkillScopeType.SPACE,
                dto.spaceId(),
                dto.name(),
                dto.displayName(),
                dto.description(),
                "空间 Skill 名称已存在",
                "SKILL_CREATED"
        );
    }

    /**
     * 创建系统 Skill。
     * <p>平台级全局技能，不属于任何租户空间，仅平台超级管理员可维护。</p>
     *
     * @param dto 系统技能创建DTO
     * @return 新建系统Skill实体
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillEntity createSystem(SystemSkillCreateDTO dto) {
        // 校验平台超级管理员角色
        requireSystemManage();
        // 触发新增操作
        return createInternal(
                // 系统类型
                SkillScopeType.SYSTEM,
                // 无空间挂载
                null,
                dto.name(),
                dto.displayName(),
                dto.description(),
                // 名称重复异常信息
                "系统 Skill 名称已存在",
                // 审计事件
                "SYSTEM_SKILL_CREATED"
        );
    }

    /**
     * 创建 Skill 的公共内部逻辑。
     * <p>统一封装名称校验、重名校验、数据库写入、审计日志，区分空间/系统技能。</p>
     *
     * @param scopeType 技能作用域类型：SPACE / SYSTEM
     * @param spaceId 空间ID，系统技能传null
     * @param name 技术名称(kebab-case)
     * @param displayName 展示名称
     * @param description 描述信息
     * @param duplicateMessage 名称冲突时抛出的提示文案
     * @param auditAction 审计事件标识
     * @return 新增完成的Skill实体
     */
    private SkillEntity createInternal(
            SkillScopeType scopeType,
            Long spaceId,
            String name,
            String displayName,
            String description,
            String duplicateMessage,
            String auditAction) {

        // 校验名称格式
        validateName(name);
        // 校验同作用域下名称唯一
        ensureNameAvailable(scopeType, spaceId, name, null, duplicateMessage);

        SkillEntity entity = new SkillEntity();
        entity.setScopeType(scopeType.name());
        entity.setSpaceId(spaceId);
        entity.setName(name);
        entity.setDisplayName(displayName);
        entity.setDescription(description);
        entity.setStatus(SkillStatus.ACTIVE.getCode());
        // 版本号初始值从1开始
        entity.setNextVersionNo(1);
        entity.setCreatedBy(AuthUtils.getUserIdOrException());

        skillMapper.insert(entity);

        // 记录创建审计日志
        auditLogService.record(
                spaceId,
                auditAction,
                "skill",
                entity.getId(),
                Map.of("name", entity.getName())
        );

        return entity;
    }

    /**
     * 查询空间Skill分页列表。
     * <p>仅查询指定空间下的SPACE类型技能；拥有空间读权限即可查看。</p>
     *
     * @param param 搜索与分页参数
     * @return 分页VO结果
     */
    public PageVO<SkillVO> list(SkillSearchParam param) {
        param.validate();

        if (param.getSpaceId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "spaceId 不能为空");
        }

        // 校验空间读权限
        requireRead(param.getSpaceId());

        LambdaQueryWrapper<SkillEntity> wrapper = new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getScopeType, SkillScopeType.SPACE.name())
                .eq(SkillEntity::getSpaceId, param.getSpaceId())
                .orderByDesc(SkillEntity::getUpdatedAt);

        // 状态筛选
        if (param.getStatus() != null) {
            wrapper.eq(SkillEntity::getStatus, param.getStatus());
        }

        // 关键词搜索条件构建（匹配Name、DisplayName、Description）
        appendKeywordCondition(wrapper, param.getKeyword());

        return listPage(param, wrapper, false);
    }

    /**
     * 查询系统 Skill。
     *
     * <p>
     * 普通用户只能看到启用中的系统 Skill；
     * 平台超级管理员可以根据 status 查询全部状态。
     * </p>
     *
     * @param param 系统技能搜索分页参数
     * @return 分页VO结果
     */
    public PageVO<SkillVO> listSystem(SystemSkillSearchParam param) {
        // 分页参数校验
        param.validate();

        // 获取当前用户是否为超级管理员
        boolean systemManager = isSystemManager();

        LambdaQueryWrapper<SkillEntity> wrapper = new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getScopeType, SkillScopeType.SYSTEM.name())
                .isNull(SkillEntity::getSpaceId)
                .orderByDesc(SkillEntity::getUpdatedAt);

        // 超管可按状态筛选；普通用户强制只看ACTIVE启用状态
        if (systemManager && param.getStatus() != null) {
            wrapper.eq(SkillEntity::getStatus, param.getStatus());
        } else if (!systemManager) {
            // 非超级管理员只能看启用状态数据
            wrapper.eq(SkillEntity::getStatus, SkillStatus.ACTIVE.getCode());
        }

        // 关键词搜索条件构建（匹配Name、DisplayName、Description）
        appendKeywordCondition(wrapper, param.getKeyword());

        return listPage(param, wrapper, !systemManager);
    }

    /**
     * 公共分页查询和 VO 组装逻辑。
     * <p>查询主表分页后批量聚合版本数量、最新版本、绑定Agent数量，避免N+1查询。</p>
     *
     * @param param         分页参数
     * @param wrapper       查询条件
     * @param publishedOnly 是否只统计已发布版本（true时不统计草稿版本）
     * @return 分页VO
     */
    private PageVO<SkillVO> listPage(
            PageParam param,
            LambdaQueryWrapper<SkillEntity> wrapper,
            boolean publishedOnly) {

        Page<SkillEntity> page = skillMapper.selectPage(
                PageUtils.toPage(param),
                wrapper
        );

        // 批量加载聚合统计数据
        SkillListSummaries summaries =
                listSummaries(page.getRecords(), publishedOnly);

        // 实体+聚合数据组装VO
        List<SkillVO> records = page.getRecords().stream()
                .map(skill -> toVO(skill, summaries))
                .toList();

        return PageVO.of(records, page.getTotal(), param);
    }

    /**
     * 为 Skill 查询追加关键字条件。
     * <p>多字段模糊搜索：技术名称 / 展示名称 / 描述，or关系。</p>
     *
     * @param wrapper query包装器
     * @param keyword 搜索关键词
     */
    private void appendKeywordCondition(
            LambdaQueryWrapper<SkillEntity> wrapper,
            String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return;
        }

        String normalizedKeyword = keyword.trim();

        wrapper.and(query -> query
                .like(SkillEntity::getName, normalizedKeyword)
                .or()
                .like(SkillEntity::getDisplayName, normalizedKeyword)
                .or()
                .like(SkillEntity::getDescription, normalizedKeyword));
    }

    /**
     * 查询系统 Skill 详情。
     * <p>权限规则：停用系统Skill仅超管可见，普通用户查询停用系统Skill返回不存在。</p>
     *
     * @param id skill主键ID
     * @return 系统Skill实体
     */
    public SkillEntity detailSystem(Long id) {
        // 查询系统Skill
        SkillEntity entity = requireSystem(id);
        // 校验系统 Skill 的读取权限（超管看所有，其他看激活）
        requireSystemRead(entity);
        return entity;
    }

    /**
     * 查询空间 Skill 详情。
     * <p>校验空间读权限，只允许访问该空间下的技能。</p>
     *
     * @param id skill主键ID
     * @return 空间Skill实体
     */
    public SkillEntity detail(Long id) {
        SkillEntity entity = requireSpace(id);
        requireRead(entity.getSpaceId());
        return entity;
    }

    /**
     * 更新系统 Skill 元数据。
     * <p>仅平台超级管理员可执行；已有版本后不允许修改技术名称name。</p>
     *
     * @param id 系统Skill主键ID
     * @param dto 更新参数
     * @return 更新后的Skill实体
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillEntity updateSystem(Long id, SkillUpdateDTO dto) {
        // 校验平台超级管理员角色
        requireSystemManage();

        // 查询系统Skill信息
        SkillEntity entity = requireSystem(id);

        // 执行更新操作
        return updateInternal(
                entity,
                dto,
                // 系统类型
                SkillScopeType.SYSTEM,
                // 重名报错信息
                "系统 Skill 名称已存在",
                // 审计事件
                "SYSTEM_SKILL_UPDATED"
        );
    }

    /**
     * 更新空间 Skill 元数据。
     * <p>需要空间管理权限；已有版本后不允许修改技术名称name。</p>
     *
     * @param id 空间Skill主键ID
     * @param dto 更新参数
     * @return 更新后的Skill实体
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillEntity update(Long id, SkillUpdateDTO dto) {
        SkillEntity entity = requireSpace(id);
        requireManage(entity.getSpaceId());

        return updateInternal(
                entity,
                dto,
                SkillScopeType.SPACE,
                "空间 Skill 名称已存在",
                "SKILL_UPDATED"
        );
    }

    /**
     * 更新 Skill 元数据的公共内部逻辑。
     * <p>核心约束：一旦存在版本记录，禁止修改skill技术名称，防止包名称与skill名称不一致。</p>
     *
     * @param entity 待更新skill实体
     * @param dto 更新入参
     * @param scopeType 作用域类型
     * @param duplicateMessage 名称冲突提示
     * @param auditAction 审计事件标识
     * @return 更新后实体
     */
    private SkillEntity updateInternal(
            SkillEntity entity,
            SkillUpdateDTO dto,
            SkillScopeType scopeType,
            String duplicateMessage,
            String auditAction) {

        // 如果修改技术名称
        if (!entity.getName().equals(dto.name())) {
            // 查询该skill下版本总数
            long versionCount = skillVersionMapper.selectCount(
                    new LambdaQueryWrapper<SkillVersionEntity>()
                            .eq(SkillVersionEntity::getSkillId, entity.getId())
            );

            // 存在版本，禁止修改name（ZIP包manifest name强绑定）
            if (versionCount > 0) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "已有版本的 Skill 不允许修改名称"
                );
            }

            // 无版本才允许修改，校验名称格式与唯一性
            validateName(dto.name());

            ensureNameAvailable(
                    scopeType,
                    entity.getSpaceId(),
                    dto.name(),
                    entity.getId(),
                    duplicateMessage
            );
        }

        entity.setName(dto.name());
        entity.setDisplayName(dto.displayName());
        entity.setDescription(dto.description());

        skillMapper.updateById(entity);

        auditLogService.record(
                entity.getSpaceId(),
                auditAction,
                "skill",
                entity.getId(),
                null
        );

        return entity;
    }

    /**
     * 启用系统 Skill。
     * <p>平台超级管理员专属操作，切换系统技能启停状态。</p>
     *
     * @param id 系统Skill主键ID
     * @param status 目标状态 ACTIVE / DISABLED
     */
    @Transactional(rollbackFor = Exception.class)
    public void setSystemStatus(Long id, SkillStatus status) {
        // 校验平台超级管理员角色
        requireSystemManage();

        SkillEntity entity = requireSystem(id);

        updateStatusInternal(
                entity,
                status,
                "SYSTEM_SKILL_ENABLED",
                "SYSTEM_SKILL_DISABLED",
                null
        );
    }

    /**
     * 启用或停用空间 Skill。
     * <p>空间管理员操作，启停本空间内的技能。停用后不可上传新版本。</p>
     *
     * @param id 空间Skill主键ID
     * @param status 目标状态 ACTIVE / DISABLED
     */
    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long id, SkillStatus status) {
        SkillEntity entity = requireSpace(id);
        requireManage(entity.getSpaceId());

        updateStatusInternal(
                entity,
                status,
                "SKILL_ENABLED",
                "SKILL_DISABLED",
                entity.getSpaceId()
        );
    }

    /**
     * 更新 Skill 状态的公共内部逻辑。
     * <p>状态无变化直接跳过；变更状态时记录对应启用/停用审计日志。</p>
     *
     * @param entity skill实体
     * @param status 目标状态
     * @param enableAuditAction 启用审计事件名
     * @param disableAuditAction 停用审计事件名
     * @param auditSpaceId 审计所属空间id，系统技能传null
     */
    private void updateStatusInternal(
            SkillEntity entity,
            SkillStatus status,
            String enableAuditAction,
            String disableAuditAction,
            Long auditSpaceId) {

        // 状态没有变更，直接返回，不产生更新和审计
        if (entity.getStatus().equals(status.getCode())) {
            return;
        }

        entity.setStatus(status.getCode());
        skillMapper.updateById(entity);

        String auditAction = status == SkillStatus.ACTIVE
                ? enableAuditAction
                : disableAuditAction;

        auditLogService.record(
                auditSpaceId,
                auditAction,
                "skill",
                entity.getId(),
                null
        );
    }

    /**
     * 按 ID 查询Skill，不存在抛NOT_FOUND。
     * <p>只做存在性校验，不校验作用域，由上层requireScope做类型校验。</p>
     *
     * @param id skill主键ID
     * @return Skill实体
     */
    public SkillEntity require(Long id) {
        SkillEntity entity = skillMapper.selectById(id);

        if (entity == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "Skill 不存在"
            );
        }

        return entity;
    }

    /**
     * 查询并确认是空间 Skill。
     * <p>校验scopeType=SPACE并且spaceId不为空，否则抛出不存在。</p>
     *
     * @param id skill主键ID
     * @return 空间Skill实体
     */
    public SkillEntity requireSpace(Long id) {
        return requireScope(
                id,
                SkillScopeType.SPACE,
                "空间 Skill 不存在"
        );
    }

    /**
     * 查询并确认是系统 Skill。
     * <p>校验scopeType=SYSTEM并且spaceId=null，否则抛出不存在。</p>
     *
     * @param id skill主键ID
     * @return 系统Skill实体
     */
    public SkillEntity requireSystem(Long id) {
        return requireScope(
                id,
                SkillScopeType.SYSTEM,
                "系统 Skill 不存在"
        );
    }

    /**
     * 校验 Skill 作用域。
     * <p>组合校验scopeType和spaceId约束，防止跨类型查询（用统一NOT_FOUND防止ID枚举探测）。</p>
     *
     * @param id skill主键ID
     * @param expectedScope 预期作用域
     * @param notFoundMessage 校验失败提示文案
     * @return 校验通过的Skill实体
     */
    private SkillEntity requireScope(
            Long id,
            SkillScopeType expectedScope,
            String notFoundMessage) {

        SkillEntity entity = require(id);
        SkillScopeType actualScope =
                SkillScopeType.fromValue(entity.getScopeType());

        boolean validScope = actualScope == expectedScope;
        // SYSTEM必须spaceId=null；SPACE必须spaceId不为null
        if (expectedScope == SkillScopeType.SYSTEM) {
            validScope = validScope && entity.getSpaceId() == null;
        } else {
            validScope = validScope && entity.getSpaceId() != null;
        }

        if (!validScope) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    notFoundMessage
            );
        }

        return entity;
    }

    /**
     * 校验 Skill 名称是否可用。
     * <p>同作用域内name唯一；更新场景通过excludedSkillId排除自身。</p>
     *
     * @param scopeType 作用域
     * @param spaceId 空间ID
     * @param name 待校验技术名称
     * @param excludedSkillId 需要排除的skillId（更新时传当前id，新增传null）
     * @param conflictMessage 重名报错文案
     */
    private void ensureNameAvailable(
            SkillScopeType scopeType,
            Long spaceId,
            String name,
            Long excludedSkillId,
            String conflictMessage) {

        LambdaQueryWrapper<SkillEntity> wrapper =
                new LambdaQueryWrapper<SkillEntity>()
                        .eq(SkillEntity::getScopeType, scopeType.name())
                        .eq(SkillEntity::getName, name);

        // 系统技能限定spaceId is null，空间技能限定spaceId匹配
        if (scopeType == SkillScopeType.SYSTEM) {
            wrapper.isNull(SkillEntity::getSpaceId);
        } else {
            wrapper.eq(SkillEntity::getSpaceId, spaceId);
        }

        // 更新场景排除自己本身
        if (excludedSkillId != null) {
            wrapper.ne(SkillEntity::getId, excludedSkillId);
        }

        Long count = skillMapper.selectCount(wrapper);

        if (count != null && count > 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    conflictMessage
            );
        }
    }

    /**
     * 要求平台超级管理员权限。
     */
    public void requireSystemManage() {
        platformAccessService.requireRole(SUPER_ADMIN);
    }

    /**
     * 判断当前用户是否是平台超级管理员。
     *
     * @return true=超管
     */
    public boolean isSystemManager() {
        return platformAccessService.hasRole(SUPER_ADMIN);
    }

    /**
     * 校验系统 Skill 的读取权限。
     *
     * <p>
     * 停用中的系统 Skill 只有平台超级管理员可见。
     * 普通用户访问停用系统Skill直接返回NOT_FOUND，隐藏资源存在性。
     * </p>
     *
     * @param skill 系统Skill实体
     */
    public void requireSystemRead(SkillEntity skill) {
        if (!SkillStatus.ACTIVE.matches(skill.getStatus())
                && !isSystemManager()) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "系统 Skill 不存在"
            );
        }
    }

    /**
     * 要求空间 Skill 管理权限。
     *
     * @param spaceId 空间ID
     */
    public void requireManage(Long spaceId) {
        spaceAccessService.requirePermission(spaceId, SKILL_MANAGE);
    }

    /**
     * 要求空间 Skill 查看权限。
     *
     * @param spaceId 空间ID
     */
    public void requireRead(Long spaceId) {
        spaceAccessService.requirePermission(spaceId, SKILL_READ);
    }

    /**
     * 刷新 Skill 的更新时间。
     *
     * <p>
     * SkillVersionService 发布版本后调用，用于刷新 Skill 列表排序。
     * </p>
     *
     * @param entity 目标skill实体
     */
    public void markUpdated(SkillEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        skillMapper.updateById(entity);
    }

    /**
     * 预分配下一个 Skill 版本号。
     *
     * <p>
     * 使用行锁(FOR UPDATE)保证并发上传时版本号不重复。
     * 取出nextVersionNo作为本次版本号，然后nextVersionNo自增+1。
     * 版本号一旦分配，就算后续上传失败，版本号也不回滚，会产生版本空洞。
     * </p>
     *
     * @param skillId skill主键ID
     * @return 本次分配到的版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public int reserveVersionNo(Long skillId) {
        SkillEntity entity = skillMapper.selectOne(
                new LambdaQueryWrapper<SkillEntity>()
                        .eq(SkillEntity::getId, skillId)
                        .last("FOR UPDATE")
        );

        if (entity == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "Skill 不存在"
            );
        }

        // 停用的Skill不能分配新版本号
        if (!SkillStatus.ACTIVE.matches(entity.getStatus())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Skill 已停用"
            );
        }

        int versionNo = entity.getNextVersionNo() == null
                ? 1
                : entity.getNextVersionNo();

        // 自增，留给下一次分配
        entity.setNextVersionNo(versionNo + 1);
        skillMapper.updateById(entity);

        return versionNo;
    }

    /**
     * 将 Skill Entity 转换为 VO。
     * <p>内部一次性批量聚合统计，封装调用listSummaries。</p>
     *
     * @param entity skill实体
     * @return SkillVO
     */
    public SkillVO toVO(SkillEntity entity) {
        SkillListSummaries summaries =
                listSummaries(List.of(entity));

        return toVO(entity, summaries);
    }

    /**
     * 使用已加载的聚合数据转换为 VO。
     * <p>复用预加载的统计结果（版本数、绑定Agent数、最新版本），避免重复查询。</p>
     *
     * @param entity skill实体
     * @param summaries 预加载聚合统计
     * @return SkillVO
     */
    private SkillVO toVO(
            SkillEntity entity,
            SkillListSummaries summaries) {

        return SkillConvertor.toVO(
                entity,
                summaries.versionCounts()
                        .getOrDefault(entity.getId(), 0L),
                summaries.boundAgentCounts()
                        .getOrDefault(entity.getId(), 0L),
                summaries.latestVersions()
                        .get(entity.getId())
        );
    }

    /**
     * 批量查询 Skill 列表所需的聚合信息。
     * <p>一次性查询版本和 Agent 绑定统计，避免逐条查询造成 N+1。默认统计全部版本（草稿+已发布）。</p>
     *
     * @param skills skill实体列表
     * @return 聚合统计记录
     */
    private SkillListSummaries listSummaries(
            List<SkillEntity> skills) {

        return listSummaries(skills, false);
    }

    /**
     * 批量查询 Skill 列表所需的聚合信息。
     *
     * @param skills skill实体列表
     * @param publishedOnly 是否只统计已发布版本，true忽略草稿版本
     * @return 聚合统计结果（版本计数、Agent绑定计数、每个skill最新版本）
     */
    private SkillListSummaries listSummaries(
            List<SkillEntity> skills,
            boolean publishedOnly) {

        if (skills.isEmpty()) {
            return new SkillListSummaries(
                    Map.of(),
                    Map.of(),
                    Map.of()
            );
        }

        List<Long> skillIds = skills.stream()
                .map(SkillEntity::getId)
                .toList();

        LambdaQueryWrapper<SkillVersionEntity> versionQuery =
                new LambdaQueryWrapper<SkillVersionEntity>()
                        .in(SkillVersionEntity::getSkillId, skillIds);

        // 仅统计已发布版本
        if (publishedOnly) {
            versionQuery.eq(
                    SkillVersionEntity::getStatus,
                    SkillVersionStatus.PUBLISHED.getCode()
            );
        }

        List<SkillVersionEntity> versions =
                skillVersionMapper.selectList(versionQuery);

        // 按skillId分组统计版本总数
        Map<Long, Long> versionCounts = versions.stream()
                .collect(Collectors.groupingBy(
                        SkillVersionEntity::getSkillId,
                        Collectors.counting()
                ));

        // 取每个skill的最大versionNo作为latestVersion
        Map<Long, SkillLatestVersionVO> latestVersions =
                new HashMap<>();
        versions.forEach(version ->
                latestVersions.compute(
                        version.getSkillId(),
                        (skillId, current) ->
                                current == null
                                        || version.getVersionNo()
                                        > current.versionNo()
                                        ? SkillVersionConvertor
                                        .toLatestVersionVO(version)
                                        : current
                )
        );

        // 查询绑定的启用Agent数量
        Map<Long, Long> boundAgentCounts =
                skillMapper.selectEnabledAgentCounts(skillIds)
                        .stream()
                        .collect(Collectors.toMap(
                                SkillBindingCountVO::getSkillId,
                                SkillBindingCountVO::getBoundAgentCount
                        ));

        return new SkillListSummaries(
                versionCounts,
                boundAgentCounts,
                latestVersions
        );
    }

    /**
     * Skill 列表聚合数据。
     * <p>内部记录类，存放批量查询得到的统计信息，用于组装SkillVO。</p>
     *
     * @param versionCounts      key:skillId value:版本总数
     * @param boundAgentCounts   key:skillId value:绑定启用Agent数量
     * @param latestVersions     key:skillId value:最新版本简要VO
     */
    private record SkillListSummaries(
            Map<Long, Long> versionCounts,
            Map<Long, Long> boundAgentCounts,
            Map<Long, SkillLatestVersionVO> latestVersions) {
    }

    /**
     * 校验 Skill 技术名称。
     * <p>必须符合kebab-case小写短横线命名规范。</p>
     *
     * @param name 技术名称
     */
    private void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "Skill 名称必须为 kebab-case"
            );
        }
    }
}