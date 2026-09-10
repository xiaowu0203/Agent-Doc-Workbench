package com.agentdoc.agent.execution.context;

import java.util.List;

/**
 * 单次执行冻结的外部 MCP 连接配置。
 *
 * @param serverId             MCP Server ID
 * @param serverKey            MCP Server 技术标识
 * @param displayName          展示名称
 * @param endpointUrl          服务端点
 * @param authType             认证类型
 * @param authParamName        Query API Key 参数名
 * @param encryptedAuthToken   加密认证令牌
 * @param configVersion        配置版本
 * @param bindingToolWhitelist Agent 绑定层工具白名单
 */
public record ExternalMcpConnection(
        Long serverId,
        String serverKey,
        String displayName,
        String endpointUrl,
        String authType,
        String authParamName,
        String encryptedAuthToken,
        Long configVersion,
        List<String> bindingToolWhitelist) {
    public ExternalMcpConnection {
        bindingToolWhitelist = bindingToolWhitelist == null ? null : List.copyOf(bindingToolWhitelist);
    }

    @Override
    public String toString() {
        return "ExternalMcpConnection[serverId=" + serverId + ", serverKey=" + serverKey
                + ", displayName=" + displayName + ", endpointUrl=" + endpointUrl
                + ", authType=" + authType + ", authParamName=" + authParamName
                + ", encryptedAuthToken=<redacted>, configVersion="
                + configVersion + ", bindingToolWhitelist=" + bindingToolWhitelist + "]";
    }
}
