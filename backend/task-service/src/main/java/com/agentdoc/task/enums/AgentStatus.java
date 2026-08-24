package com.agentdoc.task.enums;

import lombok.Getter;

/**
 * Agent 启停状态。
 */
@Getter
public enum AgentStatus {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final int code;
    private final String name;

    AgentStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static AgentStatus fromCode(Integer code) {
        for (AgentStatus status : values()) {
            if (status.code == (code == null ? 0 : code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知 Agent 状态码：" + code);
    }
}
