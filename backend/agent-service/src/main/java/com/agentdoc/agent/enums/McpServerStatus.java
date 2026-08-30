package com.agentdoc.agent.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * MCP Server 状态。
 */
@Getter
@RequiredArgsConstructor
@Schema(description = "MCP Server 状态")
public enum McpServerStatus {
    /** 已禁用。 */
    DISABLED(0),
    /** 已启用。 */
    ENABLED(1);

    private final int code;

    public boolean matches(Integer value) {
        return value != null && value == code;
    }
}
