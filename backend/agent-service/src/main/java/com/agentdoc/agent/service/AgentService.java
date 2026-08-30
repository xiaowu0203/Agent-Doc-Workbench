package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.AgentConvertor;
import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.enums.SkillSelectionMode;
import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.pojo.dto.AgentCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentUpdateDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.pojo.vo.AgentVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent配置管理服务
 * <p>
 * 负责Agent的增删改查、空间权限校验、模型可用性校验；
 * 对外提供创建、列表、详情、更新、删除、执行配置概要查询；
 * 权限通过统一空间权限服务校验；
 * 更新时由{@link AgentConvertor}维护 configVersion 配置版本号。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentMapper agentMapper;
    /** 模型服务，用于校验模型是否启用 */
    private final ModelService modelService;
    /** 空间权限校验服务 */
    private final SpaceAccessService spaceAccessService;

    /**
     * 创建Agent配置
     * <p>权限要求：空间OWNER；校验目标模型必须为启用状态；填充创建人，入库后返回VO。</p>
     *
     * @param dto Agent创建入参DTO
     * @return 新建完成AgentVO
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentVO create(AgentCreateDTO dto) {
        // 校验当前用户具备该空间OWNER权限
        spaceAccessService.requireOwner(dto.spaceId());
        // 校验引用的大模型必须处于启用状态
        modelService.requireEnabled(dto.modelId());
        validateSkillSelection(dto.skillSelectionMode(), dto.skillRouterModelId(), dto.modelId());
        AgentEntity entity = AgentConvertor.toEntity(dto, AuthUtils.getUserIdOrException());
        agentMapper.insert(entity);
        return AgentConvertor.toVO(entity);
    }

    /**
     * 查询指定空间下Agent列表，按创建时间倒序
     * <p>权限要求：空间VIEWER及以上。</p>
     *
     * @param spaceId 空间ID
     * @return AgentVO列表
     */
    public List<AgentVO> list(Long spaceId) {
        // 校验当前用户具备该空间VIEWER权限
        spaceAccessService.requireViewer(spaceId);
        return agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                        .eq(AgentEntity::getSpaceId, spaceId)
                        .orderByDesc(AgentEntity::getCreatedAt))
                .stream().map(AgentConvertor::toVO).toList();
    }

    /**
     * 获取Agent详情
     * <p>权限要求：空间VIEWER及以上。</p>
     *
     * @param id Agent主键ID
     * @return AgentVO
     */
    public AgentVO detail(Long id) {
        // 根据AgentId获取智能体信息
        AgentEntity entity = require(id);
        // 校验当前用户具备该空间VIEWER权限
        spaceAccessService.requireViewer(entity.getSpaceId());
        return AgentConvertor.toVO(entity);
    }

    /**
     * 更新Agent配置
     * <p>权限要求：空间 OWNER；校验模型启用；更新自动递增 configVersion。</p>
     *
     * @param id  Agent主键ID
     * @param dto Agent更新入参DTO
     * @return 更新后AgentVO
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentVO update(Long id, AgentUpdateDTO dto) {
        // 根据AgentId获取智能体信息
        AgentEntity entity = require(id);
        // 校验当前用户具备该空间OWNER权限
        spaceAccessService.requireOwner(entity.getSpaceId());
        // 校验引用的大模型必须处于启用状态
        modelService.requireEnabled(dto.modelId());
        validateSkillSelection(dto.skillSelectionMode(), dto.skillRouterModelId(), dto.modelId());
        // 更新Agent信息
        AgentConvertor.apply(entity, dto);
        agentMapper.updateById(entity);
        return AgentConvertor.toVO(entity);
    }

    /**
     * 删除Agent配置
     * <p>权限要求：空间OWNER。</p>
     *
     * @param id Agent主键ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 根据AgentId获取智能体信息
        AgentEntity entity = require(id);
        // 校验当前用户具备该空间OWNER权限
        spaceAccessService.requireOwner(entity.getSpaceId());
        agentMapper.deleteById(id);
    }

    /**
     * 获取Agent实体，不存在则抛出业务异常
     *
     * @param id Agent主键ID
     * @return AgentEntity数据库实体
     */
    public AgentEntity require(Long id) {
        AgentEntity entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        return entity;
    }

    /**
     * 根据ID查询Agent信息（FOR UPDATE）
     *
     * @param id Agent 主键 ID
     * @return 已锁定的 Agent 实体
     */
    public AgentEntity requireForUpdate(Long id) {
        AgentEntity entity = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getId, id)
                .last("FOR UPDATE"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        return entity;
    }

    /**
     * 更新 Agent 信息
     *
     * @param entity Agent 实体
     */
    public void updateConfiguration(AgentEntity entity) {
        agentMapper.updateById(entity);
    }

    /**
     * 获取Agent运行执行概要配置
     * <p>
     * 给Agent运行时使用，返回执行需要的关键参数：模型、token预算、文档范围、版本、价格等；
     * A2A任务启动时会调用此接口拿到运行时配置。
     * </p>
     *
     * @param id Agent主键ID
     * @return Agent执行配置概要VO
     */
    public AgentExecutionProfileVO executionProfile(Long id) {
        AgentEntity agent = require(id);
        ModelEntity model = modelService.requireEnabled(agent.getModelId());
        return new AgentExecutionProfileVO(
                agent.getId(), agent.getSpaceId(), agent.getModelId(), agent.getTokenBudget(), agent.getDocScope(),
                agent.getConfigVersion(), AgentStatus.ENABLED.matches(agent.getStatus()), model.getInputPricePerMillion(),
                model.getOutputPricePerMillion());
    }

    /**
     * 校验Skill路由配置是否合法
     * @param mode 路由类型
     * @param routerModelId 绑定的路由模型ID
     * @param mainModelId 主模型ID
     */
    private void validateSkillSelection(SkillSelectionMode mode, Long routerModelId, Long mainModelId) {
        if (mode == SkillSelectionMode.ALL_BOUND && routerModelId != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "ALL_BOUND 模式不能配置 Skill Router 模型");
        }
        if (mode == SkillSelectionMode.ROUTER && routerModelId != null && !routerModelId.equals(mainModelId)) {
            modelService.requireEnabled(routerModelId);
        }
    }
}
