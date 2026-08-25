package com.agentdoc.agent.execution.model;

import com.agentdoc.agent.constant.AgentConstant;
import com.agentdoc.agent.enums.ModelProvider;
import com.agentdoc.agent.pojo.entity.ModelEntity;
import org.apache.commons.lang3.StringUtils;

/**
 * 模型API端点解析工具类
 * <p>
 * 规则：优先使用模型数据库配置的自定义baseUrl；未配置时，根据{@link ModelProvider}返回厂商官方默认API地址。
 * 供各个厂商适配器{@code XXXModelAdapter#chatModel}构建底层Api客户端时调用。
 * </p>
 */
public final class ModelEndpoint {

    private ModelEndpoint() {
    }

    /**
     * 解析模型最终使用的baseUrl
     * <ol>
     * <li>如果model.getBaseUrl()不为空、非空白字符串，直接返回数据库存储的自定义地址（私有化/代理镜像）</li>
     * <li>为空，则根据模型提供者枚举获取厂商官方默认API baseUrl</li>
     * </ol>
     * @param model 模型实体
     * @return 模型API baseUrl地址，不会返回null
     */
    public static String resolve(ModelEntity model) {
        if (StringUtils.isNotBlank(model.getBaseUrl())) {
            return model.getBaseUrl();
        }
        return defaultBaseUrl(ModelProvider.fromCode(model.getProvider()));
    }

    /**
     * 根据模型提供者获取官方默认公网API baseUrl
     * @param provider 模型厂商枚举
     * @return 厂商默认API域名地址
     */
    public static String defaultBaseUrl(ModelProvider provider) {
        return switch (provider) {
            case OPENAI, OPENAI_COMPATIBLE -> "https://api.openai.com";
            case ANTHROPIC -> "https://api.anthropic.com";
            case GOOGLE_GEMINI -> "https://generativelanguage.googleapis.com";
            case DEEPSEEK -> "https://api.deepseek.com";
            case ZHIPU_GLM -> "https://open.bigmodel.cn/api/paas/v4";
            case ALIBABA_QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case XIAOMI_MIMO -> "https://api.xiaomimimo.com/v1";
        };
    }
}
