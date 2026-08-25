package com.agentdoc.agent.enums;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;

import java.util.Arrays;

/**
 * Agent状态枚举
 */
public enum AgentStatus {
    DISABLED(0),
    ENABLED(1);

    private final int code;

    AgentStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public boolean matches(Integer value) {
        return value != null && value == code;
    }

    public static AgentStatus fromCode(Integer code) {
        return Arrays.stream(values())
                .filter(status -> status.matches(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "无效的 Agent 状态"));
    }
}
