package com.agentdoc.common.feign.vo;

import java.time.Instant;
import java.util.List;

/**
 * 仅受控BFF协议响应，BFF不得原样转给浏览器。
 * @param credential 仅服务端持有的正式人类JWT
 * @param user 当前账号最小投影
 * @param platformRoles UI投影，不替代资源服务授权
 * @param authVersion 签发时用户认证版本
 * @param sessionId Auth生成的会话关联
 * @param authenticatedAt 固定认证起点
 * @param expiresAt 不可由刷新延长的绝对截止
 */
public record LocalHumanCredentialVO(String credential, UserRefVO user, List<String> platformRoles,
                                     Long authVersion, String sessionId, Instant authenticatedAt, Instant expiresAt) {
    @Override public String toString() { return "[REDACTED LOCAL HUMAN CREDENTIAL]"; }
}
