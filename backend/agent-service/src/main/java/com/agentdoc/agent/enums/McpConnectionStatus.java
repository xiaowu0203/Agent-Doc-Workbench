package com.agentdoc.agent.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/** MCP Server 最近一次连接测试状态。 */
@Schema(description = "MCP Server 最近一次连接测试状态")
public enum McpConnectionStatus {
    /** 尚未测试，或连接配置变更后原测试结果已失效。 */
    UNTESTED,
    /** 最近一次测试成功。 */
    SUCCESS,
    /** 最近一次测试失败。 */
    FAILED
}
