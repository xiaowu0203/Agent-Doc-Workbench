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

/** 系统 Skill 在空间中的安装、固定版本和启停管理。 */
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

    public List<SpaceSkillInstallationVO> list(Long spaceId) {
        spaceAccessService.requirePermission(spaceId, SKILL_READ);
        List<SpaceSkillInstallationEntity> installations = installationMapper.selectList(
                new LambdaQueryWrapper<SpaceSkillInstallationEntity>()
                        .eq(SpaceSkillInstallationEntity::getSpaceId, spaceId)
                        .orderByDesc(SpaceSkillInstallationEntity::getUpdatedAt));
        return toVOs(installations);
    }

    @Transactional(rollbackFor = Exception.class)
    public SpaceSkillInstallationVO install(Long spaceId, SpaceSkillInstallationCreateDTO dto) {
        spaceAccessService.requirePermission(spaceId, SKILL_MANAGE);
        if (installationMapper.selectCount(new LambdaQueryWrapper<SpaceSkillInstallationEntity>()
                .eq(SpaceSkillInstallationEntity::getSpaceId, spaceId)
                .eq(SpaceSkillInstallationEntity::getSkillId, dto.skillId())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统 Skill 已安装到当前空间");
        }
        requirePublishedSystemVersion(dto.skillId(), dto.skillVersionId(), true);

        SpaceSkillInstallationEntity entity = new SpaceSkillInstallationEntity();
        entity.setSpaceId(spaceId);
        entity.setSkillId(dto.skillId());
        entity.setSkillVersionId(dto.skillVersionId());
        entity.setEnabled(true);
        entity.setInstalledBy(AuthUtils.getUserIdOrException());
        installationMapper.insert(entity);
        auditLogService.record(spaceId, "SYSTEM_SKILL_INSTALLED", "space_skill_installation", entity.getId(),
                Map.of("skillId", dto.skillId(), "skillVersionId", dto.skillVersionId()));
        return toVOs(List.of(entity)).getFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public SpaceSkillInstallationVO update(Long spaceId, Long installationId,
                                           SpaceSkillInstallationUpdateDTO dto) {
        spaceAccessService.requirePermission(spaceId, SKILL_MANAGE);
        if (dto.skillVersionId() == null && dto.enabled() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "版本和启用状态不能同时为空");
        }
        SpaceSkillInstallationEntity entity = requireForUpdate(spaceId, installationId);
        boolean changed = false;
        if (dto.skillVersionId() != null && !dto.skillVersionId().equals(entity.getSkillVersionId())) {
            requirePublishedSystemVersion(entity.getSkillId(), dto.skillVersionId(), true);
            List<Long> agentIds = agentSkillMapper.selectEnabledAgentIdsInSpace(spaceId, entity.getSkillId());
            int updatedBindings = agentSkillMapper.updateEnabledSkillVersionInSpace(
                    spaceId, entity.getSkillId(), dto.skillVersionId());
            if (updatedBindings != agentIds.size()) {
                throw new BusinessException(ErrorCode.CONFLICT, "Agent Skill 绑定已发生并发变化，请重试");
            }
            if (!agentIds.isEmpty()) {
                agentMapper.incrementConfigVersions(agentIds);
            }
            entity.setSkillVersionId(dto.skillVersionId());
            changed = true;
        }
        if (dto.enabled() != null && !dto.enabled().equals(entity.getEnabled())) {
            entity.setEnabled(dto.enabled());
            changed = true;
        }
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

    @Transactional(rollbackFor = Exception.class)
    public void uninstall(Long spaceId, Long installationId) {
        spaceAccessService.requirePermission(spaceId, SKILL_MANAGE);
        SpaceSkillInstallationEntity entity = requireForUpdate(spaceId, installationId);
        List<Long> boundAgentIds = agentSkillMapper.selectEnabledAgentIdsInSpace(spaceId, entity.getSkillId());
        if (!boundAgentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "仍有空间 Agent 绑定该系统 Skill，无法卸载");
        }
        installationMapper.deleteById(entity.getId());
        auditLogService.record(spaceId, "SYSTEM_SKILL_UNINSTALLED", "space_skill_installation",
                entity.getId(), Map.of("skillId", entity.getSkillId(),
                        "skillVersionId", entity.getSkillVersionId()));
    }

    /** 运行时和绑定服务共同使用的空间授权校验，不依赖人类用户上下文。 */
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

    /** 批量校验系统 Skill 与固定版本，供 Agent 模板等上层用例复用。 */
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
            if (skill == null || SkillScopeType.fromValue(skill.getScopeType()) != SkillScopeType.SYSTEM
                    || skill.getSpaceId() != null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "系统 Skill 不存在");
            }
            if (requireActive && !SkillStatus.ACTIVE.matches(skill.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "系统 Skill 已停用");
            }
            if (version == null || !skill.getId().equals(version.getSkillId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "系统 Skill 版本不存在");
            }
            if (!SkillVersionStatus.PUBLISHED.matches(version.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "只能安装已发布的系统 Skill 版本");
            }
        }
    }

    private void requirePublishedSystemVersion(Long skillId, Long versionId, boolean requireActive) {
        requirePublishedSystemVersions(Map.of(skillId, versionId), requireActive);
    }

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
        if (skills.size() != expectedSkillCount || installedVersions.size() != expectedVersionCount) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统 Skill 安装数据不完整");
        }
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
            boolean upgradeAvailable = latest != null && latest.getVersionNo() > installed.getVersionNo();
            return new SpaceSkillInstallationVO(entity.getId(), entity.getSpaceId(), skill.getId(), skill.getName(),
                    skill.getDisplayName(), skill.getDescription(), installed.getId(), installed.getVersionNo(),
                    latest == null ? null : latest.getId(), latest == null ? null : latest.getVersionNo(),
                    upgradeAvailable, entity.getEnabled(), entity.getInstalledBy(), entity.getCreatedAt(),
                    entity.getUpdatedAt());
        }).toList();
    }
}
