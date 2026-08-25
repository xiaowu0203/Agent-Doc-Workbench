package com.agentdoc.agent.enums;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;

import java.util.Arrays;

/**
 * 模型供应商枚举
 */
public enum ModelProvider {
    OPENAI("openai"),
    OPENAI_COMPATIBLE("openai-compatible");

    private final String code;

    ModelProvider(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ModelProvider fromCode(String code) {
        return Arrays.stream(values())
                .filter(provider -> provider.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "暂不支持该模型供应商"));
    }
}
