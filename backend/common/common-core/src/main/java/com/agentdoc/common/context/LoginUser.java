package com.agentdoc.common.context;

import java.util.Set;

/**
 * 当前登录主体（人用户或 Agent）。
 * 由网关从 JWT 解析后以请求头透传，业务服务接收并填充到 UserContext。
 */
public record LoginUser(
        Long userId,
        String username,
        String nickname,
        Long agentId,
        Set<String> scopes
) {
    public boolean isAgent() {
        return agentId != null;
    }
}