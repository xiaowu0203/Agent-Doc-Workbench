package com.agentdoc.agent.convertor;

import com.agentdoc.agent.enums.ModelProvider;
import com.agentdoc.agent.enums.ModelStatus;
import com.agentdoc.agent.pojo.dto.ModelCreateDTO;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.agent.pojo.vo.ModelVO;

/**
 * 大模型配置对象转换器
 * <p>
 * 负责模型DTO与数据库实体、输出VO之间转换；
 * 处理模型创建入库、实体转对外视图；API‑Key入库存储加密密文，VO不返回明文密钥，仅标记密钥是否已配置。
 * </p>
 */
public final class ModelConvertor {

    private ModelConvertor() {
    }

    /**
     * 将模型创建DTO转换为数据库实体
     * <p>
     * 传入外部加密后的API‑Key密文，直接存入数据库；
     * 模型状态默认置为启用；服务商字符串转为数据库存储code码。
     * </p>
     *
     * @param dto            模型创建入参DTO
     * @param encryptedApiKey 经过加密的API‑Key密文，不在DTO内传输，由上层加密后传入
     * @return 待入库ModelEntity实体
     */
    public static ModelEntity toEntity(ModelCreateDTO dto, String encryptedApiKey) {
        ModelEntity entity = new ModelEntity();
        entity.setProvider(ModelProvider.fromCode(dto.provider()).getCode());
        entity.setModelKey(dto.modelKey());
        entity.setDisplayName(dto.displayName());
        entity.setOfficialUrl(dto.officialUrl());
        entity.setBaseUrl(dto.baseUrl());
        // 保存加密后的API Key密文，禁止明文落库
        entity.setEncryptedApiKey(encryptedApiKey);
        entity.setContextWindow(dto.contextWindow());
        entity.setMaxOutputTokens(dto.maxOutputTokens());
        entity.setInputPricePerMillion(dto.inputPricePerMillion());
        entity.setOutputPricePerMillion(dto.outputPricePerMillion());
        // 新建模型默认启用
        entity.setStatus(ModelStatus.ENABLED.getCode());
        entity.setDescription(dto.description());
        return entity;
    }

    /**
     * 数据库实体转换为对外返回VO视图
     * <p>安全处理：不返回加密后的密钥原文，仅返回布尔标识标记是否已配置API‑Key。</p>
     *
     * @param entity 模型数据库实体
     * @return 对外展示VO，状态转换为枚举对象
     */
    public static ModelVO toVO(ModelEntity entity) {
        return new ModelVO(entity.getId(), entity.getProvider(), entity.getModelKey(), entity.getDisplayName(),
                entity.getOfficialUrl(), entity.getBaseUrl(),
                // true代表已配置密钥，VO不输出密钥密文，避免密钥泄露
                entity.getEncryptedApiKey() != null,
                entity.getContextWindow(), entity.getMaxOutputTokens(), entity.getInputPricePerMillion(),
                entity.getOutputPricePerMillion(), ModelStatus.fromCode(entity.getStatus()), entity.getDescription());
    }
}
