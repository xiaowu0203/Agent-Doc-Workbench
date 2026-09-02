package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.SkillConvertor;
import com.agentdoc.agent.convertor.SkillVersionConvertor;
import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.dto.SkillCreateDTO;
import com.agentdoc.agent.pojo.dto.SkillUpdateDTO;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.param.SkillSearchParam;
import com.agentdoc.agent.pojo.vo.SkillBindingCountVO;
import com.agentdoc.agent.pojo.vo.SkillLatestVersionVO;
import com.agentdoc.agent.pojo.vo.SkillVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
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

import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_READ;

/**
 * Skill元数据服务
 * <p>
 * 负责Skill主实体的创建、查询、元数据更新、启用/停用、版本号预分配、权限校验；
 * 只操作 {@link SkillEntity}；版本包上传/发布/解析由 {@code SkillVersionService} 负责。
 * 约束：
 * <ul>
 *     <li>Skill名称为kebab‑case小写短横线格式；同一space下名称唯一</li>
 *     <li>一旦产生版本记录，不允许修改Skill名称</li>
 *     <li>nextVersionNo为Skill内自增版本号，通过行锁预分配，保证版本号不重复</li>
 *     <li>空间权限通过documentFeign远程调用校验；变更操作记录审计日志</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    /**
     * Skill名称正则：kebab‑case，小写字母数字，短横线分隔，不能以横线开头结尾
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final SkillMapper skillMapper;
    private final SkillVersionMapper skillVersionMapper;
    private final SpaceAccessService spaceAccessService;
    private final SkillAuditLogService auditLogService;

    /**
     * 创建全新Skill
     *
     * @param dto 创建参数，spaceId、name、description
     * @return 持久化后的Skill实体
    */
    @Transactional(rollbackFor = Exception.class)
    public SkillEntity create(SkillCreateDTO dto) {
        // 校验Skill名称
        validateName(dto.name());

        // 校验该空间是否存在一样的Skill名称
        Long count = skillMapper.selectCount(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getSpaceId, dto.spaceId())
                .eq(SkillEntity::getName, dto.name()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "空间内 Skill 名称已存在");
        }

        // 新增
        SkillEntity entity = new SkillEntity();
        entity.setSpaceId(dto.spaceId());
        entity.setName(dto.name());
        entity.setDisplayName(dto.displayName());
        entity.setDescription(dto.description());
        entity.setStatus(SkillStatus.ACTIVE.getCode());
        entity.setNextVersionNo(1);
        entity.setCreatedBy(AuthUtils.getUserIdOrException());
        skillMapper.insert(entity);

        // 写入Skill 审计
        auditLogService.record(entity.getSpaceId(), "SKILL_CREATED", "skill", entity.getId(),
                Map.of("name", entity.getName()));
        return entity;
    }

    /**
     * 分页查询空间下Skill列表，附带每个Skill的版本总数
     *
     * @param param 查询条件：spaceId、status、keyword、分页参数
     * @return 分页VO
     */
    public PageVO<SkillVO> list(SkillSearchParam param) {
        // 参数校验
        param.validate();
        if (param.getSpaceId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "spaceId 不能为空");
        }

        // 校验空间查看权限
        requireRead(param.getSpaceId());

        LambdaQueryWrapper<SkillEntity> wrapper = new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getSpaceId, param.getSpaceId())
                .orderByDesc(SkillEntity::getUpdatedAt);
        if (param.getStatus() != null) {
            wrapper.eq(SkillEntity::getStatus, param.getStatus());
        }
        if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
            String keyword = param.getKeyword().trim();
            wrapper.and(query -> query.like(SkillEntity::getName, keyword)
                    .or().like(SkillEntity::getDisplayName, keyword)
                    .or().like(SkillEntity::getDescription, keyword));
        }
        Page<SkillEntity> page = skillMapper.selectPage(PageUtils.toPage(param), wrapper);

        SkillListSummaries summaries = listSummaries(page.getRecords());

        return PageVO.of(page.getRecords().stream()
                .map(skill -> SkillConvertor.toVO(skill,
                        summaries.versionCounts().getOrDefault(skill.getId(), 0L),
                        summaries.boundAgentCounts().getOrDefault(skill.getId(), 0L),
                        summaries.latestVersions().get(skill.getId())))
                .toList(),
                page.getTotal(), param);
    }

    /**
     * 查询Skill详情（元数据）
     *
     * @param id skill主键
     * @return Skill实体
     */
    public SkillEntity detail(Long id) {
        SkillEntity entity = require(id);
        requireRead(entity.getSpaceId());
        return entity;
    }

    /**
     * 更新Skill元数据：描述、名称；<b>已有版本后禁止修改名称</b>
     *
     * @param id  skillId
     * @param dto 更新参数
     * @return 更新后实体
     */
    @Transactional(rollbackFor = Exception.class)
    public SkillEntity update(Long id, SkillUpdateDTO dto) {
        SkillEntity entity = require(id);
        requireManage(entity.getSpaceId());

        // 如果要修改名称，校验：只要存在任意版本，不允许改名
        if (!entity.getName().equals(dto.name())) {
            long versions = skillVersionMapper.selectCount(new LambdaQueryWrapper<SkillVersionEntity>()
                    .eq(SkillVersionEntity::getSkillId, id));
            if (versions > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "已有版本的 Skill 不允许修改名称");
            }
            // 校验名称
            validateName(dto.name());
        }
        entity.setName(dto.name());
        entity.setDisplayName(dto.displayName());
        entity.setDescription(dto.description());
        skillMapper.updateById(entity);
        auditLogService.record(entity.getSpaceId(), "SKILL_UPDATED", "skill", entity.getId(), null);
        return entity;
    }

    /**
     * 设置Skill启用/停用状态
     * <p>注意：仅修改SkillEntity状态；不会自动批量更新AgentSkill绑定关系，运行期做业务拦截</p>
     *
     * @param id     skillId
     * @param status ACTIVE / DISABLED
     */
    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long id, SkillStatus status) {
        SkillEntity entity = require(id);
        requireManage(entity.getSpaceId());
        // 状态无变化直接返回，避免产生不必要审计日志
        if (entity.getStatus().equals(status.getCode())) {
            return;
        }
        entity.setStatus(status.getCode());
        skillMapper.updateById(entity);
        auditLogService.record(entity.getSpaceId(), status == SkillStatus.ACTIVE ? "SKILL_ENABLED" : "SKILL_DISABLED",
                "skill", entity.getId(), null);
    }

    /**
     * 校验Skill存在并返回实体，不存在抛NOT_FOUND
     *
     * @param id skillId
     * @return SkillEntity
     */
    public SkillEntity require(Long id) {
        SkillEntity entity = skillMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 不存在");
        }
        return entity;
    }

    /**
     * 要求当前用户拥有 Skill 管理权限
     */
    public void requireManage(Long spaceId) {
        spaceAccessService.requirePermission(spaceId, SKILL_MANAGE);
    }

    /**
     * 要求当前用户拥有 Skill 查看权限
     */
    public void requireRead(Long spaceId) {
        spaceAccessService.requirePermission(spaceId, SKILL_READ);
    }

    /**
     * 刷新 Skill 最近更新时间，用于版本发布后的列表排序。
     * 调用方必须已经完成该 Skill 的管理权限和归属校验。
     *
     * @param entity 待刷新的Skill实体
     */
    public void markUpdated(SkillEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        skillMapper.updateById(entity);
    }

    /**
     * 预分配下一个版本号，行锁保证并发安全
     * <p>新建版本前调用；取出nextVersionNo，再+1写回数据库，返回本次使用的versionNo</p>
     *
     * @param skillId skill主键
     * @return 本次分配的版本号（从1开始自增）
     */
    @Transactional(rollbackFor = Exception.class)
    public int reserveVersionNo(Long skillId) {
        // 行锁锁住Skill记录，防止并发修改绑定关系
        SkillEntity entity = skillMapper.selectOne(new LambdaQueryWrapper<SkillEntity>()
                .eq(SkillEntity::getId, skillId)
                .last("FOR UPDATE"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Skill 不存在");
        }
        if (!SkillStatus.ACTIVE.matches(entity.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Skill 已停用");
        }
        int versionNo = entity.getNextVersionNo() == null ? 1 : entity.getNextVersionNo();
        entity.setNextVersionNo(versionNo + 1);
        skillMapper.updateById(entity);
        return versionNo;
    }

    /**
     * 转换为VO，内部实时查询该Skill的版本总数
     *
     * @param entity skill实体
     * @return SkillVO
     */
    public SkillVO toVO(SkillEntity entity) {
        SkillListSummaries summaries = listSummaries(List.of(entity));
        return SkillConvertor.toVO(entity,
                summaries.versionCounts().getOrDefault(entity.getId(), 0L),
                summaries.boundAgentCounts().getOrDefault(entity.getId(), 0L),
                summaries.latestVersions().get(entity.getId()));
    }

    /**
     * 批量统计一批Skill各自的版本数量，用于列表页展示，减少数据库IO
     *
     * @param skills skill实体列表
     * @return Map&lt;skillId, 版本数&gt;
     */
    private SkillListSummaries listSummaries(List<SkillEntity> skills) {
        if (skills.isEmpty()) {
            return new SkillListSummaries(Map.of(), Map.of(), Map.of());
        }
        List<Long> skillIds = skills.stream().map(SkillEntity::getId).toList();
        List<SkillVersionEntity> versions = skillVersionMapper.selectList(
                new LambdaQueryWrapper<SkillVersionEntity>()
                        .in(SkillVersionEntity::getSkillId, skillIds));
        Map<Long, Long> versionCounts = versions.stream()
                .collect(Collectors.groupingBy(SkillVersionEntity::getSkillId, Collectors.counting()));
        Map<Long, SkillLatestVersionVO> latestVersions = new HashMap<>();
        versions.forEach(version -> latestVersions.compute(version.getSkillId(), (skillId, current) ->
                current == null || version.getVersionNo() > current.versionNo()
                        ? SkillVersionConvertor.toLatestVersionVO(version) : current));
        Map<Long, Long> boundAgentCounts = skillMapper.selectEnabledAgentCounts(skillIds).stream()
                .collect(Collectors.toMap(SkillBindingCountVO::getSkillId, SkillBindingCountVO::getBoundAgentCount));
        return new SkillListSummaries(versionCounts, boundAgentCounts, latestVersions);
    }

    private record SkillListSummaries(
            Map<Long, Long> versionCounts,
            Map<Long, Long> boundAgentCounts,
            Map<Long, SkillLatestVersionVO> latestVersions) {
    }

    /**
     * Skill名校验：kebab‑case格式
     *
     * @param name skill名称
     */
    private void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Skill 名称必须为 kebab-case");
        }
    }

}
