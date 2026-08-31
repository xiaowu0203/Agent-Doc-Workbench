package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.McpServerConvertor;
import com.agentdoc.agent.enums.McpAuthType;
import com.agentdoc.agent.mapper.McpServerMapper;
import com.agentdoc.agent.pojo.dto.McpServerCreateDTO;
import com.agentdoc.agent.pojo.dto.McpServerUpdateDTO;
import com.agentdoc.agent.pojo.entity.McpServerEntity;
import com.agentdoc.agent.pojo.param.McpServerSearchParam;
import com.agentdoc.agent.pojo.vo.McpServerVO;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.agent.security.McpEndpointSecurityValidator;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.PageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;

import static com.agentdoc.common.constant.SpacePermissionConstant.MCP_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.MCP_READ;

/**
 * MCP服务实例业务服务
 * <p>
 * 负责MCP Server实例的创建、查询、修改、删除；
 * 包含空间权限校验、地址安全校验、鉴权令牌加密、配置版本号维护；
 * 删除时会校验是否存在正在被Agent启用的绑定关系，防止误删正在使用的MCP服务。
 * 使用TransactionTemplate编程式事务 + 行级悲观锁(FOR UPDATE)防止并发写冲突。
 */
@Service
@RequiredArgsConstructor
public class McpServerService {
    private final McpServerMapper mapper;
    private final AgentMcpBindingQueryService bindingQueryService;
    private final SpaceAccessService spaceAccessService;
    private final AgentConfigCryptoService cryptoService;
    private final McpEndpointSecurityValidator endpointValidator;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建MCP Server实例
     * <p>
     * 校验：空间所有者权限、外部端点URL安全校验；
     * 同一空间下serverKey唯一；鉴权令牌加密存储；初始配置版本=1。
     *
     * @param dto 创建请求DTO
     * @return 创建完成的MCP Server视图对象
    */
    public McpServerVO create(McpServerCreateDTO dto) {
        // 事务外执行权限和网络校验，避免长期占用数据库连接。
        // 校验空间所有者权限
        spaceAccessService.requirePermission(dto.spaceId(), MCP_MANAGE);
        // 校验外部端点URL安全
        endpointValidator.validateExternal(dto.endpointUrl());
        return transactionTemplate.execute(status -> createLocked(dto));
    }

    /**
     * 事务内执行创建逻辑
     * <p>
     * 先业务层count校验serverKey重复，再执行insert；捕获数据库唯一索引冲突兜底。
     *
     * @param dto 创建请求DTO
     * @return MCP Server视图对象
     */
    private McpServerVO createLocked(McpServerCreateDTO dto) {
        // 校验MCP是否重复（SpaceId+ServerKey）
        if (mapper.selectCount(new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getSpaceId, dto.spaceId())
                .eq(McpServerEntity::getServerKey, dto.serverKey())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "空间内 MCP serverKey 已存在");
        }

        // 类型转换(encryptedToken：令牌加密)
        McpServerEntity entity = McpServerConvertor.toEntity(dto,
                encryptedToken(dto.authType(), dto.authToken(), null));
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "空间内 MCP serverKey 已存在");
        }
        return McpServerConvertor.toVO(entity);
    }

    /**
     * 分页查询MCP Server列表
     * 按空间隔离，支持状态过滤、关键词模糊搜索(serverKey/显示名称)，按serverKey升序
     *
     * @param param 搜索分页参数
     * @return 分页VO，包含MCP Server视图集合与总条数
     */
    public PageVO<McpServerVO> list(McpServerSearchParam param) {
        // 参数校验
        param.validate();
        // 校验用户具备该空间查看权限
        spaceAccessService.requirePermission(param.getSpaceId(), MCP_READ);

        // 构建查询
        LambdaQueryWrapper<McpServerEntity> query = new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getSpaceId, param.getSpaceId())
                .orderByAsc(McpServerEntity::getServerKey);
        if (param.getStatus() != null) {
            query.eq(McpServerEntity::getStatus, param.getStatus());
        }
        if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
            String keyword = param.getKeyword().trim();
            query.and(
                    value -> value.like(McpServerEntity::getServerKey, keyword)
                    .or()
                    .like(McpServerEntity::getDisplayName, keyword)
            );
        }

        // 进行分页查询
        Page<McpServerEntity> page = mapper.selectPage(PageUtils.toPage(param), query);
        // 类型转换
        return PageVO.of(page.getRecords().stream().map(McpServerConvertor::toVO).toList(),
                page.getTotal(), param);
    }

    /**
     * 查询MCP Server详情
     *
     * @param id MCP Server主键ID
     * @return MCP Server视图对象
     */
    public McpServerVO detail(Long id) {
        // 查询并校验实体是否存在
        McpServerEntity entity = require(id);
        // 校验空间查看权限
        spaceAccessService.requirePermission(entity.getSpaceId(), MCP_READ);
        return McpServerConvertor.toVO(entity);
    }

    /**
     * 更新MCP Server
     * <p>
     * 前置校验：空间所有者权限、外部端点URL安全校验；
     * 实际更新逻辑运行在事务内 {@link #updateLocked(Long, McpServerUpdateDTO)}
     *
     * @param id MCP Server主键ID
     * @param dto 更新请求DTO
     * @return 更新后的MCP Server视图对象
     */
    public McpServerVO update(Long id, McpServerUpdateDTO dto) {
        McpServerEntity current = require(id);
        // 校验用户是空间所有者
        spaceAccessService.requirePermission(current.getSpaceId(), MCP_MANAGE);
        // 校验更新后的外部端点URL安全性
        endpointValidator.validateExternal(dto.endpointUrl());
        // 事务内执行更新
        return transactionTemplate.execute(status -> updateLocked(id, dto));
    }

    /**
     * 事务内执行MCP Server更新逻辑
     * <p>
     * 使用行锁锁定记录，DTO字段覆盖实体，鉴权令牌按需加密：
     * 如果不传新令牌，则保留数据库原有加密令牌
     *
     * @param id MCP Server主键ID
     * @param dto 更新请求DTO
     * @return 更新完成的MCP Server视图对象
     */
    private McpServerVO updateLocked(Long id, McpServerUpdateDTO dto) {
        // for update行锁查询，校验记录存在
        McpServerEntity entity = requireForUpdate(id);
        // DTO字段应用到实体，处理令牌加密逻辑（encryptedToken：令牌加密）
        McpServerConvertor.apply(entity, dto, encryptedToken(dto.authType(), dto.authToken(),
                entity.getEncryptedAuthToken()));
        mapper.updateById(entity);
        return McpServerConvertor.toVO(entity);
    }

    /**
     * 删除MCP Server
     * <p>
     * 前置校验：空间所有者权限；事务内校验是否存在启用的Agent绑定关系，存在则禁止删除
     *
     * @param id MCP Server主键ID
     */
    public void delete(Long id) {
        McpServerEntity current = require(id);
        // 校验空间所有者权限
        spaceAccessService.requirePermission(current.getSpaceId(), MCP_MANAGE);
        // 事务内执行删除逻辑，无返回值
        transactionTemplate.executeWithoutResult(status -> deleteLocked(id));
    }

    /**
     * 事务内执行删除逻辑
     * <p>
     * 行锁锁定记录；校验不能存在已启用的Agent绑定，防止删除正在使用的MCP Server
     *
     * @param id MCP Server主键ID
     */
    private void deleteLocked(Long id) {
        // for update行锁校验记录存在
        requireForUpdate(id);
        // 校验是否存在启用状态的Agent绑定关系
        if (bindingQueryService.hasEnabledBinding(id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "MCP Server 仍被 Agent 绑定");
        }
        mapper.deleteById(id);
    }

    /**
     * 根据ID查询MCP Server实体，不存在抛出NOT_FOUND业务异常
     *
     * @param id MCP Server主键ID
     * @return MCP Server数据库实体
     */
    public McpServerEntity require(Long id) {
        McpServerEntity entity = mapper.selectById(id);
        if (entity == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "MCP Server 不存在");
        return entity;
    }

    /**
     * 批量获取 MCP Server。
     *
     * @param ids MCP Server ID 集合
     * @return MCP Server 实体列表
     */
    public List<McpServerEntity> findByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectBatchIds(ids);
    }

    /**
     * 在当前事务中按主键顺序锁定 MCP Server。
     *
     * @param ids MCP Server ID 集合
     * @return 已锁定的 MCP Server 实体列表
     */
    public List<McpServerEntity> findByIdsForUpdate(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<McpServerEntity>()
                .in(McpServerEntity::getId, ids)
                .orderByAsc(McpServerEntity::getId)
                .last("FOR UPDATE"));
    }

    /**
     * 事务内行锁查询单条MCP Server，用于更新/删除场景，不存在抛出NOT_FOUND
     *
     * @param id MCP Server主键ID
     * @return 被FOR UPDATE锁定的MCP Server实体
     */
    private McpServerEntity requireForUpdate(Long id) {
        McpServerEntity entity = mapper.selectOne(new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getId, id)
                .last("FOR UPDATE"));
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "MCP Server 不存在");
        }
        return entity;
    }

    /**
     * 鉴权令牌加密工具方法
     * <p>
     * 逻辑：
     * 1.认证类型为NONE直接返回null；
     * 2.新令牌为空：优先复用数据库已存在的加密令牌；无旧令牌则抛出参数校验异常；
     * 3.新令牌不为空：调用加密服务加密明文令牌返回密文。
     *
     * @param type        MCP认证类型
     * @param value       传入的明文令牌
     * @param existing    数据库中原有的加密令牌（更新场景传入，创建场景传null）
     * @return 加密后的令牌密文，无认证返回null
     */
    private String encryptedToken(McpAuthType type, String value, String existing) {
        if (type == McpAuthType.NONE)
            return null;
        if (StringUtils.isBlank(value)) {
            // 更新场景：前端不传新令牌，则沿用库中旧密文
            if (StringUtils.isNotBlank(existing))
                return existing;
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "BEARER 认证必须提供令牌");
        }
        return cryptoService.encrypt(value);
    }

}
