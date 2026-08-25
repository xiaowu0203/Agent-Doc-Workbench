package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.enums.ModelAdapterType;
import com.agentdoc.agent.enums.ModelProvider;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 模型适配器注册表，集中完成配置到运行时适配器的解析。 */
@Component
public class ModelAdapterRegistry {

    private final Map<ModelAdapterType, ModelAdapter> adapters;

    public ModelAdapterRegistry(List<ModelAdapter> modelAdapters) {
        EnumMap<ModelAdapterType, ModelAdapter> registry = new EnumMap<>(ModelAdapterType.class);
        modelAdapters.forEach(adapter -> adapter.supportedTypes().forEach(type -> {
            if (registry.put(type, adapter) != null) {
                throw new IllegalStateException("模型适配器重复注册: " + type.getCode());
            }
        }));
        this.adapters = Map.copyOf(registry);
    }

    public ModelAdapter require(ModelEntity model) {
        ModelProvider provider = ModelProvider.fromCode(model.getProvider());
        ModelAdapterType type = ModelAdapterType.fromCodeOrDefault(model.getAdapterType(), provider);
        ModelAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模型适配器未启用: " + type.getCode());
        }
        return adapter;
    }

    /**
     * 按任务要求校验模型能力并返回适配器。
     *
     * @param model    模型配置
     * @param required 当前任务要求的能力
     * @return 满足能力要求的适配器
     */
    public ModelAdapter require(ModelEntity model, ModelCapabilities required) {
        ModelAdapter adapter = require(model);
        if (!adapter.capabilities().supports(required)) {
            String reason = required.toolCalling() && !adapter.capabilities().toolCalling()
                    ? "模型不支持工具调用"
                    : "模型不支持并行工具调用";
            throw new BusinessException(ErrorCode.BAD_REQUEST, reason + "，无法执行当前任务");
        }
        return adapter;
    }
}
