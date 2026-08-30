package com.agentdoc.agent.service;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.constant.SkillConstant;
import com.agentdoc.agent.convertor.AgentSkillConvertor;
import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.AgentSkillMapper;
import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.dto.AgentSkillReplaceDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentSkillEntity;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.vo.AgentSkillBindingVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent‑Skill绑定关系服务
 * <p>
 * 负责Agent与Skill之间绑定关系的查询、全量替换绑定。
 * 包含权限校验、参数校验、状态校验、数据库关联维护、Agent配置版本号递增、审计日志记录。
 * 业务约束：一个Agent同一个Skill只允许绑定一个版本；只能绑定已发布且启用状态的Skill。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentSkillService {

    private final AgentService agentService;
    private final SpaceAccessService spaceAccessService;
    private final AgentMapper agentMapper;
    private final AgentSkillMapper agentSkillMapper;
    private final SkillMapper skillMapper;
    private final SkillVersionMapper versionMapper;
    private final SkillAuditLogService auditLogService;

    /**
     * 查询Agent已绑定的Skill列表
     *
     * @param agentId Agent主键ID
     * @return 绑定关系VO列表
     */
    public List<AgentSkillBindingVO> list(Long agentId) {
        // 校验Agent存在性
        AgentEntity agent = agentService.require(agentId);
        // 校验空间查看权限
        spaceAccessService.requireViewer(agent.getSpaceId());
        // 加载已启用的绑定关系
        return loadBindings(agentId, true);
    }

    /**
     * 全量替换Agent绑定的Skill版本集合
     * <p>
     * 事务方法：根据传入skillVersionIds全量更新绑定关系：新增绑定、切换版本、更新启用状态。
     * 会校验：ID重复、绑定数量上限、版本存在、版本已发布、Skill归属空间、Skill状态；
     * 同一个Skill不允许同时绑定多个版本；
     * 发生变更时递增Agent配置版本号，并写入审计日志。
     * </p>
     *
     * @param agentId Agent主键ID
     * @param dto     待绑定Skill版本ID集合DTO
     * @return 更新之后Agent生效的Skill绑定VO列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<AgentSkillBindingVO> replace(Long agentId, AgentSkillReplaceDTO dto) {
        // 行锁锁住Agent记录，防止并发修改绑定关系
        AgentEntity agent = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getId, agentId)
                .last("FOR UPDATE"));
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        // 校验空间所有者权限，只有空间拥有者可修改Agent的Skill绑定
        spaceAccessService.requireOwner(agent.getSpaceId());

        // 获取请求绑定的版本ID集合，null转为空列表
        List<Long> requestedIds = dto.skillVersionIds() == null ? List.of() : dto.skillVersionIds();

        // 校验绑定数量不能超过最大允许绑定数
        if (requestedIds.size() > SkillConstant.MAX_BINDINGS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Agent 绑定 Skill 数量超过限制");
        }

        // 校验传入版本ID不能重复
        if (requestedIds.size() != new HashSet<>(requestedIds).size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Skill 版本 ID 不能重复");
        }

        // 批量查询请求的Skill版本，校验版本是否全部存在
        Map<Long, SkillVersionEntity> versions = new HashMap<>();
        if (!requestedIds.isEmpty()) {
            versionMapper.selectBatchIds(requestedIds).forEach(
                    version -> versions.put(version.getId(), version)
            );
        }
        if (versions.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 版本不存在");
        }

        // 提取版本对应的Skill主键ID集合
        Set<Long> requestedSkillIds = versions.values().stream()
                .map(SkillVersionEntity::getSkillId)
                .collect(Collectors.toSet());
        Map<Long, SkillEntity> skills = new HashMap<>();

        // 批量查询Skill主表信息
        if (!requestedSkillIds.isEmpty()) {
            skillMapper.selectBatchIds(requestedSkillIds).forEach(
                    skill -> skills.put(skill.getId(), skill)
            );
        }

        Set<Long> boundSkillIds = new HashSet<>();
        for (Long versionId : requestedIds) {
            SkillVersionEntity version = versions.get(versionId);
            // 只能绑定已发布状态的Skill版本
            if (!SkillVersionStatus.PUBLISHED.matches(version.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "只能绑定已发布 Skill 版本");
            }

            // 校验Skill存在，且Skill属于Agent所在空间，跨空间不允许绑定
            SkillEntity skill = skills.get(version.getSkillId());
            if (skill == null || !agent.getSpaceId().equals(skill.getSpaceId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Skill 不属于 Agent 所在空间");
            }

            // 校验Skill主状态为启用，不能绑定已停用Skill
            if (!SkillStatus.ACTIVE.matches(skill.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "不能绑定已停用 Skill");
            }

            // 校验同一个Skill不能绑定多个不同版本
            if (!boundSkillIds.add(skill.getId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "同一个 Agent 不能绑定同一 Skill 的多个版本");
            }
            skills.put(skill.getId(), skill);
        }

        // 查询当前Agent已有的全部绑定关系
        List<AgentSkillEntity> current = agentSkillMapper.selectList(new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getAgentId, agentId));
        Map<Long, AgentSkillEntity> currentBySkill = current.stream()
                .collect(Collectors.toMap(AgentSkillEntity::getSkillId, value -> value));

        // key:SkillId  value:待绑定的SkillVersionId
        Map<Long, Long> requestedBySkill = new HashMap<>();
        versions.values().forEach(version -> requestedBySkill.put(version.getSkillId(), version.getId()));

        // 标记是否发生数据库变更，用于判断是否更新Agent配置版本号与审计日志
        boolean changed = false;

        // 处理存量绑定记录：更新启用状态、切换版本
        for (AgentSkillEntity relation : current) {
            Long requestedVersion = requestedBySkill.get(relation.getSkillId());
            // 判断当前这条绑定是否需要保持启用
            boolean enabled = requestedVersion != null && requestedVersion.equals(relation.getSkillVersionId());
            // 更新启用状态
            if (relation.getEnabled() == null || relation.getEnabled() != enabled) {
                relation.setEnabled(enabled);
                agentSkillMapper.updateById(relation);
                changed = true;
            }
            // Skill仍然在新绑定列表，但版本发生变更，更新版本并启用
            if (enabled && requestedVersion != null && !requestedVersion.equals(relation.getSkillVersionId())) {
                relation.setSkillVersionId(requestedVersion);
                relation.setEnabled(true);
                agentSkillMapper.updateById(relation);
                changed = true;
            }
        }

        // 处理新增绑定：Skill之前未绑定，本次需要新增绑定关系
        for (Map.Entry<Long, Long> entry : requestedBySkill.entrySet()) {
            AgentSkillEntity relation = currentBySkill.get(entry.getKey());
            if (relation == null) {
                // 新建Agent‑Skill绑定记录
                relation = new AgentSkillEntity();
                relation.setAgentId(agentId);
                relation.setSkillId(entry.getKey());
                relation.setSkillVersionId(entry.getValue());
                relation.setEnabled(true);
                agentSkillMapper.insert(relation);
                changed = true;
            } else if (!entry.getValue().equals(relation.getSkillVersionId()) || !Boolean.TRUE.equals(relation.getEnabled())) {
                // 已存在记录，版本或者启用状态不一致，执行更新
                relation.setSkillVersionId(entry.getValue());
                relation.setEnabled(true);
                agentSkillMapper.updateById(relation);
                changed = true;
            }
        }

        // 如果绑定关系发生变更：递增Agent配置版本号，记录审计日志
        if (changed) {
            long configVersion = agent.getConfigVersion() == null
                    ? AgentConstant.INITIAL_CONFIG_VERSION : agent.getConfigVersion();
            agent.setConfigVersion(configVersion + AgentConstant.CONFIG_VERSION_INCREMENT);
            agentMapper.updateById(agent);
            auditLogService.record(agent.getSpaceId(), "AGENT_SKILLS_REPLACED", "agent", agentId,
                    Map.of("skillVersionIds", requestedIds));
        }

        // 返回更新完成后的绑定视图
        return loadBindings(agentId, true);
    }

    /**
     * 加载Agent的Skill绑定关系，组装VO对象
     *
     * @param agentId      Agent主键ID
     * @param enabledOnly  true只查询已启用的绑定；false查询全部绑定记录（包含禁用）
     * @return 组装后的VO列表
     */
    private List<AgentSkillBindingVO> loadBindings(Long agentId, boolean enabledOnly) {
        LambdaQueryWrapper<AgentSkillEntity> wrapper = new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getAgentId, agentId).orderByAsc(AgentSkillEntity::getSkillId);
        if (enabledOnly) {
            wrapper.eq(AgentSkillEntity::getEnabled, true);
        }
        List<AgentSkillEntity> relations = agentSkillMapper.selectList(wrapper);
        if (relations.isEmpty()) {
            return List.of();
        }

        // 批量查询关联Skill主表
        Map<Long, SkillEntity> skills = new HashMap<>();
        skillMapper.selectBatchIds(relations.stream().map(AgentSkillEntity::getSkillId)
                .collect(Collectors.toSet()))
                .forEach(skill -> skills.put(skill.getId(), skill));

        // 批量查询关联Skill版本表
        Map<Long, SkillVersionEntity> versions = new HashMap<>();
        versionMapper.selectBatchIds(relations.stream().map(AgentSkillEntity::getSkillVersionId)
                .collect(Collectors.toSet()))
                .forEach(version -> versions.put(version.getId(), version));

        // 转换为VO返回
        return relations.stream().map(relation -> {
            SkillEntity skill = skills.get(relation.getSkillId());
            SkillVersionEntity version = versions.get(relation.getSkillVersionId());
            return AgentSkillConvertor.toVO(relation, skill, version);
        }).toList();
    }
}
