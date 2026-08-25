package com.agentdoc.agent.enums;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;

import java.util.Arrays;

/**
 * 模型适配器类型枚举
 * <p>
 * 【设计依据】区分「业务厂商ModelProvider」和「底层技术适配器」
 * provider = 用户选择的大模型厂商(openai/anthropic/google‑gemini等)
 * adapterType = 底层调用协议/SDK实现方式，同一个厂商可切换多种适配器
 * 例如：通义千问可以使用 openai‑compatible，未来可切换 dashscope‑native
 * </p>
 * <p>
 * 支持根据code字符串解析适配器；提供带厂商兜底的解析方法，数据库adapterType为空时，使用厂商默认适配器
 * </p>
 */
public enum ModelAdapterType {
    /** OpenAI 原生 chat/completions 接口适配器 */
    OPENAI_CHAT("openai-chat"),
    /** OpenAI兼容协议适配器，用于GLM / Qwen / MiMo / DeepSeek兼容模式 */
    OPENAI_COMPATIBLE("openai-compatible"),
    /** Anthropic Claude 原生 Messages API 适配器，不建议走openai兼容层 */
    ANTHROPIC_MESSAGES("anthropic-messages"),
    /** Google Gemini 原生 GenAI SDK 适配器 */
    GOOGLE_GENAI("google-genai");

    private final String code;

    ModelAdapterType(String code) {
        this.code = code;
    }
/**
 * 获取代码的方法
 * @return 返回code属性的值
 */

    public String getCode() { // 返回code变量的值
        return code;
    }

    /**
     * 根据编码字符串解析适配器类型
     * <p>兼容别名：例如传入"openai"、"claude"这类简写别名，映射到对应标准适配器</p>
     * @param code 适配器编码/简写别名，可以来自前端、数据库
     * @return 匹配到的ModelAdapterType
     * @throws BusinessException BAD_REQUEST 当传入code无法匹配任何适配器时抛出
     */
    public static ModelAdapterType fromCode(String code) {
        if (code != null) {
            // 兼容外部传入简写别名，映射到标准适配器
            switch (code.trim().toLowerCase()) {
                case "openai", "openai-chat-completions" -> {
                    return OPENAI_CHAT;
                }
                case "anthropic", "claude", "messages" -> {
                    return ANTHROPIC_MESSAGES;
                }
                case "gemini", "google" -> {
                    return GOOGLE_GENAI;
                }
                default -> {
                    // 别名未命中，继续使用标准code精确匹配逻辑
                }
            }
        }
        // 使用枚举内部定义的标准code做忽略大小写精确匹配
        return Arrays.stream(values())
                .filter(type -> type.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "暂不支持该模型适配器"));
    }

    /**
     * 获取适配器，带厂商默认兜底策略
     * <p>当数据库/入参adapterType为空时，自动使用该模型厂商定义的默认适配器</p>
     * @param code 适配器编码，允许null/blank
     * @param provider 模型厂商，不为null
     * @return 解析后的适配器类型
     * @throws BusinessException code非空但是无法识别适配器时抛出
     */
    public static ModelAdapterType fromCodeOrDefault(String code, ModelProvider provider) {
        // 入参为空，使用厂商内置默认适配器
        return code == null || code.isBlank() ? provider.defaultAdapterType() : fromCode(code);
    }
}
