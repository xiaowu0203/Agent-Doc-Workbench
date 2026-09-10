package com.agentdoc.agent.enums;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;

import java.util.Arrays;

/**
 * 模型供应商枚举
 */
public enum ModelProvider {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    GOOGLE_GEMINI("google-gemini"),
    DEEPSEEK("deepseek"),
    ZHIPU_GLM("zhipu-glm"),
    ALIBABA_QWEN("alibaba-qwen"),
    XIAOMI_MIMO("xiaomi-mimo"),
    OPENAI_COMPATIBLE("openai-compatible");

    private final String code;

    ModelProvider(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 获取该厂商默认的底层技术适配器
     * <p>返回供应商的默认适配器；存在多协议入口时由 {@link #resolveAdapterType(String)} 进一步判断。</p>
     * @return 默认适配器类型，保证不会返回null
     */
    public ModelAdapterType defaultAdapterType() {
        return switch (this) {
            case OPENAI -> ModelAdapterType.OPENAI_CHAT;
            case ANTHROPIC -> ModelAdapterType.ANTHROPIC_MESSAGES;
            case GOOGLE_GEMINI -> ModelAdapterType.GOOGLE_GENAI;
            case DEEPSEEK, ZHIPU_GLM, ALIBABA_QWEN, XIAOMI_MIMO, OPENAI_COMPATIBLE ->
                    ModelAdapterType.OPENAI_COMPATIBLE;
        };
    }

    /**
     * 根据供应商和服务地址确定实际调用协议。
     * <p>DeepSeek 同时提供 OpenAI 与 Anthropic 兼容入口；使用 /anthropic 地址时自动切换为
     * Anthropic Messages 适配器，其余情况使用供应商默认适配器。</p>
     *
     * @param baseUrl 模型服务基础地址
     * @return 与地址协议匹配的适配器
     */
    public ModelAdapterType resolveAdapterType(String baseUrl) {
        if (this == DEEPSEEK && baseUrl != null) {
            String normalizedBaseUrl = baseUrl.trim().toLowerCase();
            if (normalizedBaseUrl.endsWith("/anthropic") || normalizedBaseUrl.contains("/anthropic/")) {
                return ModelAdapterType.ANTHROPIC_MESSAGES;
            }
        }
        return defaultAdapterType();
    }

    /**
     * 根据编码解析供应商，兼容前端传入的各类简写别名
     * @param code 厂商编码/简写别名；允许来自前端、数据库
     * @return 匹配到的ModelProvider
     * @throws BusinessException BAD_REQUEST 无法识别供应商时抛出
     */
    public static ModelProvider fromCode(String code) {
        if (code != null) {
            switch (code.trim().toLowerCase()) {
                case "gemini", "google", "google-gemini" -> {
                    return GOOGLE_GEMINI;
                }
                case "claude" -> {
                    return ANTHROPIC;
                }
                case "deep-seek" -> {
                    return DEEPSEEK;
                }
                case "glm", "zhipu", "zhipuai" -> {
                    return ZHIPU_GLM;
                }
                case "qwen", "dashscope", "tongyi", "tongyi-qianwen" -> {
                    return ALIBABA_QWEN;
                }
                case "mimo", "xiaomi" -> {
                    return XIAOMI_MIMO;
                }
                case "ollama" -> {
                    return OPENAI_COMPATIBLE;
                }
                default -> {
                    // 别名未命中，进入标准code匹配
                }
            }
        }
        // 使用已经trim+小写后的字符串做后续匹配，避免空格大小写问题
        return Arrays.stream(values())
                .filter(provider -> provider.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "暂不支持该模型供应商"));
    }
}
