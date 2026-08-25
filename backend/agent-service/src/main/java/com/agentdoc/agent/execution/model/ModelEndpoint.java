package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.enums.ModelProvider;
import com.agentdoc.agent.pojo.entity.ModelEntity;

/**
 * 模型接口地址解析。显式配置优先，常见供应商提供默认地址。
 */
public final class ModelEndpoint {

    private ModelEndpoint() {
    }

    public static String resolve(ModelEntity model) {
        if (model.getBaseUrl() != null && !model.getBaseUrl().isBlank()) {
            return model.getBaseUrl();
        }
        return defaultBaseUrl(ModelProvider.fromCode(model.getProvider()));
    }

    public static String defaultBaseUrl(ModelProvider provider) {
        return switch (provider) {
            case OPENAI, OPENAI_COMPATIBLE -> AgentConstant.DEFAULT_OPENAI_BASE_URL;
            case ANTHROPIC -> "https://api.anthropic.com";
            case GOOGLE_GEMINI -> "https://generativelanguage.googleapis.com";
            case DEEPSEEK -> "https://api.deepseek.com";
            case ZHIPU_GLM -> "https://open.bigmodel.cn/api/paas/v4";
            case ALIBABA_QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case XIAOMI_MIMO -> "https://api.xiaomimimo.com/v1";
        };
    }
}
