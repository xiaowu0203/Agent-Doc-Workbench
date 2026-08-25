package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.ModelConvertor;
import com.agentdoc.agent.execution.model.ModelAdapterContext;
import com.agentdoc.agent.execution.model.ModelAdapterRegistry;
import com.agentdoc.agent.execution.model.ModelChatModelCache;
import com.agentdoc.agent.execution.model.ModelProviderException;
import com.agentdoc.agent.enums.ModelStatus;
import com.agentdoc.agent.mapper.ModelMapper;
import com.agentdoc.agent.pojo.dto.ModelCreateDTO;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.pojo.vo.ModelConnectionTestVO;
import com.agentdoc.agent.pojo.vo.ModelVO;
import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collections;

/**
 * 大模型配置管理服务
 * <p>
 * 负责模型配置的查询、创建、状态启停；
 * 创建模型时对API‑Key做AES‑GCM加密后落库；
 * 提供获取模型实体、强校验模型是否启用的工具方法，供Agent创建/更新、任务运行时校验使用。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ModelService {

    private final ModelMapper modelMapper;
    /** 配置加密服务，用于模型API‑Key加密存储 */
    private final AgentConfigCryptoService cryptoService;
    /** 模型适配器注册表，用于配置保存前的连通性测试 */
    private final ModelAdapterRegistry adapterRegistry;
    /** 模型状态或配置变化时主动清理旧 ChatModel 实例 */
    private final ModelChatModelCache chatModelCache;

    /**
     * 查询模型列表
     *
     * @param enabledOnly true：仅查询启用的模型；false/null：查询全部模型
     * @return 模型VO集合
     */
    public List<ModelVO> list(Boolean enabledOnly) {
        // 校验登录身份，必须携带用户身份上下文
        AuthUtils.getUserIdOrException();
        LambdaQueryWrapper<ModelEntity> wrapper = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(enabledOnly)) {
            wrapper.eq(ModelEntity::getStatus, ModelStatus.ENABLED.getCode());
        }
        return modelMapper.selectList(wrapper).stream().map(ModelConvertor::toVO).toList();
    }

    /**
     * 创建模型配置
     * <p>对传入的明文apiKey加密，密文存入数据库，VO不会返回密钥信息。</p>
     *
     * @param dto 模型创建入参DTO，携带明文API‑Key
     * @return 创建完成的模型VO
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelVO create(ModelCreateDTO dto) {
        AuthUtils.getUserIdOrException();
        // 将明文API‑Key加密后传入转换器生成实体
        ModelEntity entity = ModelConvertor.toEntity(dto, cryptoService.encrypt(dto.apiKey()));
        modelMapper.insert(entity);
        return ModelConvertor.toVO(entity);
    }

    /**
     * 测试尚未保存的模型配置。
     * <p>明文 API Key 只进入本次适配器调用，不写入数据库。</p>
     *
     * @param dto 模型配置和明文 API Key
     * @return 连接测试结果；模型供应商异常以统一错误字段返回
     */
    public ModelConnectionTestVO testConnect(ModelCreateDTO dto) {
        AuthUtils.getUserIdOrException();
        ModelEntity model = ModelConvertor.toEntity(dto, null);
        ModelAdapterContext context = new ModelAdapterContext(null, model, dto.apiKey(), 1,
                Collections.emptyList());
        try {
            adapterRegistry.require(model).testConnect(context);
            return new ModelConnectionTestVO(true, model.getProvider(), null, null, false, "模型连接成功");
        } catch (ModelProviderException exception) {
            return new ModelConnectionTestVO(false, exception.getProvider(), exception.getErrorType(),
                    exception.getStatusCode(), exception.isRetryable(), exception.getMessage());
        }
    }

    /**
     * 更新模型启用/禁用状态
     *
     * @param id     模型主键ID
     * @param status 目标状态编码 {@link ModelStatus}
     * @return 更新后模型VO
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelVO updateStatus(Long id, Integer status) {
        AuthUtils.getUserIdOrException();
        ModelEntity entity = require(id);
        entity.setStatus(ModelStatus.fromCode(status).getCode());
        modelMapper.updateById(entity);
        chatModelCache.invalidate(id);
        return ModelConvertor.toVO(entity);
    }

    /**
     * 供模型配置更新流程在递增 configVersion 后主动清理旧实例。
     * 当前服务已提供的状态更新也会执行同样的失效逻辑。
     */
    public void invalidateChatModelCache(Long id) {
        chatModelCache.invalidate(id);
    }

    /**
     * 获取模型实体，不存在抛出业务异常
     *
     * @param id 模型主键ID
     * @return 模型数据库实体
     */
    public ModelEntity require(Long id) {
        ModelEntity entity = modelMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型不存在");
        }
        return entity;
    }

    /**
     * 获取模型并强校验模型必须处于启用状态
     * <p>供Agent管理、任务运行时调用；模型不存在或已禁用直接抛业务异常阻断流程。</p>
     *
     * @param id 模型主键ID
     * @return 已启用的模型实体
     */
    public ModelEntity requireEnabled(Long id) {
        ModelEntity entity = require(id);
        if (!ModelStatus.ENABLED.matches(entity.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模型已禁用");
        }
        return entity;
    }
}
