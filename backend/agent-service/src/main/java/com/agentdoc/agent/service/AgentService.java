package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.AgentConvertor;
import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.pojo.dto.AgentCreateDTO;
import com.agentdoc.agent.pojo.dto.AgentUpdateDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.pojo.vo.AgentVO;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.SpaceRole;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
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
 * 权限通过Feign远程调用document‑service做空间角色鉴权；
 * 更新时由{@link AgentConvertor}维护configVersion乐观锁版本号。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentMapper agentMapper;
    /** 模型服务，用于校验模型是否启用 */
    private final ModelService modelService;
    /** Feign客户端，远程调用文档空间服务，做空间权限校验 */
    private final DocumentFeign documentFeign;

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
        requireSpaceRole(dto.spaceId(), SpaceRole.OWNER);
        // 校验引用的大模型必须处于启用状态
        modelService.requireEnabled(dto.modelId());
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
        requireSpaceRole(spaceId, SpaceRole.VIEWER);
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
        requireSpaceRole(entity.getSpaceId(), SpaceRole.VIEWER);
        return AgentConvertor.toVO(entity);
    }

    /**
     * 更新Agent配置
     * <p>权限要求：空间OWNER；校验模型启用；更新自动递增configVersion乐观锁版本号。</p>
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
        requireSpaceRole(entity.getSpaceId(), SpaceRole.OWNER);
        // 校验引用的大模型必须处于启用状态
        modelService.requireEnabled(dto.modelId());
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
        requireSpaceRole(entity.getSpaceId(), SpaceRole.OWNER);
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
     * 远程校验当前用户在指定空间具备对应角色权限
     * <p>Feign调用document‑service空间权限接口；校验不通过直接抛出业务异常。</p>
     *
     * @param spaceId 空间ID
     * @param role    需要具备的最小角色
     */
    private void requireSpaceRole(Long spaceId, SpaceRole role) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, role.getCode());
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "空间权限校验失败" : result.message());
        }
    }
}
