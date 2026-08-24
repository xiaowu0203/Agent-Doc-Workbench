package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.SpaceRole;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import com.agentdoc.task.enums.ModelStatus;
import com.agentdoc.task.mapper.AgentMapper;
import com.agentdoc.task.mapper.ModelMapper;
import com.agentdoc.task.pojo.dto.AgentCreateDTO;
import com.agentdoc.task.pojo.dto.AgentUpdateDTO;
import com.agentdoc.task.pojo.entity.AgentEntity;
import com.agentdoc.task.pojo.entity.ModelEntity;
import com.agentdoc.task.pojo.vo.AgentVO;
import com.agentdoc.task.security.McpConfigCryptoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Agent业务服务
 * <p>
 * 负责空间内Agent的增删改查生命周期管理；包含权限校验、MCP配置加密存储、审计日志记录。
 * </p>
 * <p>权限规则：
 * <ul>
 * <li>创建/更新/删除Agent：要求用户为空间 OWNER 所有者权限；</li>
 * <li>列表/查询Agent详情：要求用户为空间 VIEWER 及以上成员权限；</li>
 * <li>Agent绑定的模型必须为已启用状态；</li>
 * <li>MCP配置敏感字符串不存储明文，通过 {@link McpConfigCryptoService} 加密入库。</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentMapper agentMapper;
    private final ModelMapper modelMapper;
    private final DocumentFeign documentFeign;
    private final McpConfigCryptoService cryptoService;
    private final AuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public AgentVO create(AgentCreateDTO dto) {
        // 校验当前用户必须是空间所有者
        requireSpaceOwner(dto.spaceId());
        // 校验模型存在并且状态为启用
        requireEnabledModel(dto.modelId());
        // MCP配置加密，不保存明文；填充创建人userId
        AgentEntity entity = dto.toEntity(
                cryptoService.encrypt(dto.mcpConfig()), AuthUtils.getUserIdOrException());
        agentMapper.insert(entity);
        // 审计日志
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.AGENT_CREATED,
                AuditTargetType.AGENT, entity.getId(), null);
        return entity.toVO();
    }

    /**
     * 查询指定空间下全部Agent列表，按创建时间倒序
     * @param spaceId 空间ID
     * @return AgentVO列表
     */
    public List<AgentVO> list(Long spaceId) {
        // 校验用户为空间查看者及以上权限
        requireSpaceMember(spaceId);
        return agentMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                        .eq(AgentEntity::getSpaceId, spaceId)
                        .orderByDesc(AgentEntity::getCreatedAt))
                .stream().map(AgentEntity::toVO).toList();
    }

    /**
     * 获取Agent详情
     * @param id Agent主键ID
     * @return AgentVO
     */
    public AgentVO detail(Long id) {
        AgentEntity entity = require(id);
        requireSpaceMember(entity.getSpaceId());
        return entity.toVO();
    }

    /**
     * 更新Agent信息
     * <p>
     * 校验：空间所有者权限；校验绑定模型为已启用；
     * 如果传入MCP配置则重新加密；更新数据库，记录审计日志。
     * </p>
     * @param id Agent主键ID
     * @param dto Agent更新入参
     * @return 更新后AgentVO
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentVO update(Long id, AgentUpdateDTO dto) {
        AgentEntity entity = require(id);
        // 校验当前用户必须是空间所有者
        requireSpaceOwner(entity.getSpaceId());
        // 校验模型存在并且状态为启用
        requireEnabledModel(dto.modelId());
        // 新MCP配置不为空则加密；为空则设置为null
        String encryptedMcpConfig = dto.mcpConfig() == null || dto.mcpConfig().isBlank()
                ? null : cryptoService.encrypt(dto.mcpConfig());
        dto.applyTo(entity, encryptedMcpConfig);
        agentMapper.updateById(entity);
        // 审计日志
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.AGENT_UPDATED,
                AuditTargetType.AGENT, entity.getId(), null);
        return entity.toVO();
    }

    /**
     * 删除Agent
     * @param id Agent主键ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AgentEntity entity = require(id);
        // 权限校验
        requireSpaceOwner(entity.getSpaceId());
        agentMapper.deleteById(id);
        // 审计日志
        auditLogService.recordHuman(entity.getSpaceId(), AuditAction.AGENT_DELETED,
                AuditTargetType.AGENT, entity.getId(), null);
    }

    /**
     * 获取Agent实体，不存在抛出NOT_FOUND业务异常
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
     * 校验模型存在并且状态为已启用
     * @param modelId 模型ID
     * @throws BusinessException 模型不存在或者已禁用抛出BAD_REQUEST
     */
    private void requireEnabledModel(Long modelId) {
        ModelEntity model = modelMapper.selectById(modelId);
        if (model == null || !Integer.valueOf(ModelStatus.ENABLED.getCode()).equals(model.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模型不存在或已禁用");
        }
    }

    /**
     * 校验当前用户是该空间所有者 OWNER
     * @param spaceId 空间ID
     * @throws BusinessException 权限不足或远程调用失败抛出异常
     */
    private void requireSpaceOwner(Long spaceId) {
        requireRemoteSuccess(documentFeign.checkSpacePermission(spaceId, SpaceRole.OWNER.getCode()));
    }

    /**
     * 校验当前用户是该空间成员 VIEWER及以上
     * @param spaceId 空间ID
     * @throws BusinessException 权限不足或远程调用失败抛出异常
     */
    private void requireSpaceMember(Long spaceId) {
        requireRemoteSuccess(documentFeign.checkSpacePermission(spaceId, SpaceRole.VIEWER.getCode()));
    }

    /**
     * Feign远程调用结果统一校验工具，非成功响应抛出业务异常
     * @param result feign返回Result对象
     * @throws BusinessException result为空 / code非SUCCESS抛出对应业务异常
     */
    private void requireRemoteSuccess(Result<?> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "空间权限校验失败" : result.message());
        }
    }

}
