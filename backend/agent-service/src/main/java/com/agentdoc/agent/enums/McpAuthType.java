package com.agentdoc.agent.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "MCP 认证类型")
public enum McpAuthType {
    /** 无认证。 */
    NONE,
    /** Bearer Token 认证。 */
    BEARER,
    /** 将加密保存的 API Key 作为 URL query 参数发送。 */
    QUERY_PARAM
}
