package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.SkillScopeType;
import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.mapper.AgentSkillMapper;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.mapper.SpaceSkillInstallationMapper;
import com.agentdoc.agent.pojo.dto.SpaceSkillInstallationCreateDTO;
import com.agentdoc.agent.pojo.dto.SpaceSkillInstallationUpdateDTO;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.entity.SpaceSkillInstallationEntity;
import com.agentdoc.agent.pojo.vo.SpaceSkillInstallationVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_READ;

/**
 * 系统 Skill 在空间中的安装、固定版本和启停管理。
 *
 * <p>
 * 负责将平台级 SYSTEM Skill 安装到指定租户空间，绑定固定版本，支持版本切换、启用/停用、卸载。
 * 安装记录属于空间维度资源，空间管理员维护；安装后空间内Agent才可绑定使用该系统Skill。
 * 约束：仅能安装【已发布】的系统Skill版本；卸载前必须解除所有空间Agent绑定。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SpaceSkillInstallationService {

    private final SpaceSkillInstallationMapper installationMapper;
    private final SkillMapper skillMapper;
    private final SkillVersionMapper versionMapper;
    private final AgentSkillMapper agentSkillMapper;
    private final AgentMapper agentMapper;
    private final SpaceAccessService spaceAccessService;
    private final SkillAuditLogService auditLogService;

    /**
     * 查询当前空间下所有已安装的系统Skill安装记录。
     *
     * @param spaceId 空间ID
     * @return 安装记录VO列表，附带当前安装版本、最新发布版本、是否可升级标识
     */
    public List<SpaceSkillInstallationVO> list(Long spaceId) {
        // 校验空间查看权限
        spaceAccessService.requirePermission(spaceId, SKILL_READ);
        List<SpaceSkillInstallationEntity> installations = installationMapper.selectList(
                new LambdaQueryWrapper<SpaceSkillInstallationEntity>()
                        .eq(SpaceSkillInstallationEntity::getSpaceId, spaceId)
                        .orderByDesc(SpaceSkillInstallationEntity::getUpdatedAt));
        return toVOs(installations);
    }

    /**
     * 在空间内安装系统Skill，绑定指定已发布版本。
     *
     * <p>同一空间同一个系统Skill只能存在一条安装记录；安装后默认启用。</p>
     *
     * @param spaceId 空间ID
     * @param dto 安装入参（skillId + skillVersionId）
     * @return 安装记录VO
     */
    @Transactional(rollbackFor = Exception.class)
    public SpaceSkillInstallationVO install(Long spaceId, SpaceSkillInstallationCreateDTO dto) {
        // 校验空间管理权限
        spaceAccessService.requirePermission(spaceId, SKILL_MANAGE);
        // 校验安装记录唯一性
        if (installationMapper.selectCount(new LambdaQueryWrapper<SpaceSkillInstallationEntity>()
                .eq(SpaceSkillInstallationEntity::getSpaceId, spaceId)
                .eq(SpaceSkillInstallationEntity::getSkillId, dto.skillId())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统 Skill 已安装到当前空间");
        }
        // 校验目标版本必须是已发布的系统Skill
        requirePublishedSystemVersion(dto.skillId(), dto.skillVersionId(), true);

        // 新增系统Skill-空间安装记录
        SpaceSkillInstallationEntity entity = new SpaceSkillInstallationEntity();
        entity.setSpaceId(spaceId);
        entity.setSkillId(dto.skillId());
        entity.setSkillVersionId(dto.skillVersionId());
        entity.setEnabled(true);
        entity.setInstalledBy(AuthUtils.getUserIdOrException());
        installationMapper.insert(entity);

        // 审计日志
        auditLogService.record(spaceId, "SYSTEM_SKILL_INSTALLED", "space_skill_installation", entity.getId(),
                Map.of("skillId", dto.skillId(), "skillVersionId", dto.skillVersionId()));
        return toVOs(List.of(entity)).getFirst();
    }

    /**
     * 更新系统Skill安装配置：切换绑定版本 / 修改启用状态，二者可单独或同时修改。
     *
     * <p>版本切换时会批量更新当前空间内所有Agent上该Skill绑定的版本号，
     * 并递增Agent配置版本号触发Agent配置重载；使用行锁保证安装记录并发安全。</p>
     *
     * @param spaceId 空间ID
     * @param installationId 安装记录主键ID
     * @param dto 更新参数（版本号、启用状态，不能同时为空）
     * @return 更新后的安装记录VO
     */
    @Transactional(rollbackFor = Exception.class)
    public SpaceSkillInstallationVO update(Long spaceId, Long installationId,
                                           SpaceSkillInstallationUpdateDTO dto) {
        spaceAccessService.requirePermission(spaceId, SKILL_MANAGE);
        if (dto.skillVersionId() == null && dto.enabled() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "版本和启用状态不能同时为空");
        }
        // 行锁锁定当前安装记录，防止并发修改
        SpaceSkillInstallationEntity entity = requireForUpdate(spaceId, installationId);
        boolean changed = false;
        // 切换安装版本逻辑
        if (dto.skillVersionId() != null && !dto.skillVersionId().equals(entity.getSkillVersionId())) {
            requirePublishedSystemVersion(entity.getSkillId(), dto.skillVersionId(), true);
            // 查询空间内所有绑定该Skill且启用状态的AgentId
            List<Long> agentIds = agentSkillMapper.selectEnabledAgentIdsInSpace(spaceId, entity.getSkillId());
            // 批量更新Agent绑定的版本号
            int updatedBindings = agentSkillMapper.updateEnabledSkillVersionInSpace(
                    spaceId, entity.getSkillId(), dto.skillVersionId());
            // 并发校验：预期更新行数必须和查到的Agent数量完全一致，否则说明绑定关系发生变更
            if (updatedBindings != agentIds.size()) {
                throw new BusinessException(ErrorCode.CONFLICT, "Agent Skill 绑定已发生并发变化，请重试");
            }
            // 递增Agent配置版本，通知Agent重新加载配置
            if (!agentIds.isEmpty()) {
                agentMapper.incrementConfigVersions(agentIds);
            }
            entity.setSkillVersionId(dto.skillVersionId());
            changed = true;
        }

        // 修改启用状态
        if (dto.enabled() != null && !dto.enabled().equals(entity.getEnabled())) {
            entity.setEnabled(dto.enabled());
            changed = true;
        }

        // 有变更才执行数据库更新和审计日志
        if (changed) {
            installationMapper.updateById(entity);
            Map<String, Object> detail = new HashMap<>();
            detail.put("skillId", entity.getSkillId());
            detail.put("skillVersionId", entity.getSkillVersionId());
            detail.put("enabled", entity.getEnabled());
            auditLogService.record(spaceId, "SYSTEM_SKILL_INSTALLATION_UPDATED",
                    "space_skill_installation", entity.getId(), detail);
        }
        return toVOs(List.of(entity)).getFirst();
    }

    /**
     * 卸载空间内已安装的系统Skill。
     *
     * <p>前置校验：空间内不能存在任何Agent绑定该Skill，否则禁止卸载。</p>
     *
     * @param spaceId 空间ID
     * @param installationId 安装记录主键ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void uninstall(Long spaceId, Long installationId) {
        // 校验空间管理权限
        spaceAccessService.requirePermission(spaceId, SKILL_MANAGE);
        // 行锁锁定当前安装记录，防止并发修改
        SpaceSkillInstallationEntity entity = requireForUpdate(spaceId, installationId);
        // 校验空间内是否仍有Agent绑定该Skill
        List<Long> boundAgentIds = agentSkillMapper.selectEnabledAgentIdsInSpace(spaceId, entity.getSkillId());
        // 校验：空间内不能存在任何Agent绑定该Skill，否则禁止卸载
        if (!boundAgentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "仍有空间 Agent 绑定该系统 Skill，无法卸载");
        }
        // 删除安装记录
        installationMapper.deleteById(entity.getId());
        // 审计日志
        auditLogService.record(spaceId, "SYSTEM_SKILL_UNINSTALLED", "space_skill_installation",
                entity.getId(), Map.of("skillId", entity.getSkillId(),
                        "skillVersionId", entity.getSkillVersionId()));
    }

    /**
     * 运行时和绑定服务共同使用的空间授权校验
     *
     * <p>Agent执行Skill时调用，校验：该空间已安装目标系统Skill、安装记录启用、版本完全匹配。</p>
     *
     * @param spaceId 空间ID
     * @param skillId 系统Skill主键
     * @param skillVersionId 安装绑定的版本ID
     */
    public void requireEnabledInstallation(Long spaceId, Long skillId, Long skillVersionId) {
        Long count = installationMapper.selectCount(new LambdaQueryWrapper<SpaceSkillInstallationEntity>()
                .eq(SpaceSkillInstallationEntity::getSpaceId, spaceId)
                .eq(SpaceSkillInstallationEntity::getSkillId, skillId)
                .eq(SpaceSkillInstallationEntity::getSkillVersionId, skillVersionId)
                .eq(SpaceSkillInstallationEntity::getEnabled, true));
        if (count == null || count != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统 Skill 未安装、已停用或版本不匹配");
        }
    }

    /**
     * 带行锁查询安装记录，校验归属空间，用于更新/卸载事务内锁定资源，防止并发修改。
     *
     * @param spaceId 空间ID
     * @param installationId 安装记录ID
     * @return 锁定后的安装实体
     */
    private SpaceSkillInstallationEntity requireForUpdate(Long spaceId, Long installationId) {
        SpaceSkillInstallationEntity entity = installationMapper.selectOne(
                new LambdaQueryWrapper<SpaceSkillInstallationEntity>()
                        .eq(SpaceSkillInstallationEntity::getId, installationId)
                        .eq(SpaceSkillInstallationEntity::getSpaceId, spaceId)
                        .last("FOR UPDATE"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统 Skill 安装记录不存在");
        }
        return entity;
    }

    /**
     * 批量校验系统 Skill 与固定版本，供 Agent 模板等上层用例复用。
     *
     * <p>批量校验：必须是SYSTEM类型Skill、版本存在、版本状态为已发布；可附加校验Skill本身是否启用。</p>
     *
     * @param versionBySkillId key:skillId value:skillVersionId
     * @param requireActive 是否强制要求系统Skill主状态为ACTIVE启用
     */
    public void requirePublishedSystemVersions(Map<Long, Long> versionBySkillId, boolean requireActive) {
        if (versionBySkillId == null || versionBySkillId.isEmpty()) {
            return;
        }
        Map<Long, SkillEntity> skills = skillMapper.selectBatchIds(versionBySkillId.keySet()).stream()
                .collect(Collectors.toMap(SkillEntity::getId, Function.identity()));
        Map<Long, SkillVersionEntity> versions = versionMapper.selectBatchIds(
                        versionBySkillId.values().stream().collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SkillVersionEntity::getId, Function.identity()));
        for (Map.Entry<Long, Long> entry : versionBySkillId.entrySet()) {
            SkillEntity skill = skills.get(entry.getKey());
            SkillVersionEntity version = versions.get(entry.getValue());
            // 校验必须是系统Skill，spaceId必须为空
            if (skill == null || SkillScopeType.fromValue(skill.getScopeType()) != SkillScopeType.SYSTEM
                    || skill.getSpaceId() != null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "系统 Skill 不存在");
            }
            // 可选校验系统Skill主状态是否启用
            if (requireActive && !SkillStatus.ACTIVE.matches(skill.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "系统 Skill 已停用");
            }
            // 校验版本归属关系
            if (version == null || !skill.getId().equals(version.getSkillId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "系统 Skill 版本不存在");
            }
            // 仅允许已发布版本安装
            if (!SkillVersionStatus.PUBLISHED.matches(version.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "只能安装已发布的系统 Skill 版本");
            }
        }
    }

    /**
     * 单版本校验包装方法，复用批量校验逻辑。
     *
     * @param skillId 系统SkillID
     * @param versionId 版本ID
     * @param requireActive 是否强制系统Skill启用
     */
    private void requirePublishedSystemVersion(Long skillId, Long versionId, boolean requireActive) {
        requirePublishedSystemVersions(Map.of(skillId, versionId), requireActive);
    }

    /**
     * 将安装实体列表转为VO，批量查询关联Skill、版本、最新发布版本，避免N+1。
     *
     * <p>VO内计算upgradeAvailable标识：对比当前安装版本号与该Skill全局最新发布版本。</p>
     *
     * @param installations 安装实体列表
     * @return VO列表
     */
    private List<SpaceSkillInstallationVO> toVOs(List<SpaceSkillInstallationEntity> installations) {
        if (installations.isEmpty()) {
            return List.of();
        }
        Map<Long, SkillEntity> skills = skillMapper.selectBatchIds(installations.stream()
                        .map(SpaceSkillInstallationEntity::getSkillId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SkillEntity::getId, Function.identity()));
        Map<Long, SkillVersionEntity> installedVersions = versionMapper.selectBatchIds(installations.stream()
                        .map(SpaceSkillInstallationEntity::getSkillVersionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SkillVersionEntity::getId, Function.identity()));
        long expectedSkillCount = installations.stream().map(SpaceSkillInstallationEntity::getSkillId)
                .distinct().count();
        long expectedVersionCount = installations.stream().map(SpaceSkillInstallationEntity::getSkillVersionId)
                .distinct().count();
        // 校验外键完整性，防止脏数据
        if (skills.size() != expectedSkillCount || installedVersions.size() != expectedVersionCount) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统 Skill 安装数据不完整");
        }
        // 查询每个系统Skill最新已发布版本
        Map<Long, SkillVersionEntity> latestVersions = versionMapper.selectList(
                        new LambdaQueryWrapper<SkillVersionEntity>()
                                .in(SkillVersionEntity::getSkillId, skills.keySet())
                                .eq(SkillVersionEntity::getStatus, SkillVersionStatus.PUBLISHED.getCode()))
                .stream().collect(Collectors.toMap(SkillVersionEntity::getSkillId, Function.identity(),
                        (left, right) -> left.getVersionNo() >= right.getVersionNo() ? left : right));

        return installations.stream().map(entity -> {
            SkillEntity skill = skills.get(entity.getSkillId());
            SkillVersionEntity installed = installedVersions.get(entity.getSkillVersionId());
            if (skill == null || installed == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "系统 Skill 安装数据不完整");
            }
            SkillVersionEntity latest = latestVersions.get(entity.getSkillId());
            // 判断是否存在更高版本可升级
            boolean upgradeAvailable = latest != null && latest.getVersionNo() > installed.getVersionNo();
            return new SpaceSkillInstallationVO(entity.getId(), entity.getSpaceId(), skill.getId(), skill.getName(),
                    skill.getDisplayName(), skill.getDescription(), installed.getId(), installed.getVersionNo(),
                    latest == null ? null : latest.getId(), latest == null ? null : latest.getVersionNo(),
                    upgradeAvailable, entity.getEnabled(), entity.getInstalledBy(), entity.getCreatedAt(),
                    entity.getUpdatedAt());
        }).toList();
    }
}
