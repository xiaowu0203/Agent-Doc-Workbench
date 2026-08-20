package com.agentdoc.common.security;

import com.agentdoc.common.context.LoginUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * JWT 解析工具：将（已校验的）Jwt 映射为 LoginUser，供网关与业务服务复用。
 */
public class JwtTokenParser {

    /**
     * 使用给定解码器解析并校验 token。
     */
    public Jwt decode(String token, JwtDecoder decoder) {
        return decoder.decode(token);
    }

    /**
     * 将已校验的 Jwt 声明映射为 LoginUser。
     * 约定声明：sub=用户 ID、username、nickname、scope（逗号分隔）、agentId（Agent 访问时）。
     */
    public LoginUser toLoginUser(Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        String username = jwt.getClaimAsString("username");
        String nickname = jwt.getClaimAsString("nickname");
        if (nickname == null || nickname.isBlank()) {
            nickname = username;
        }
        Long agentId = null;
        if (jwt.hasClaim("agentId")) {
            Object value = jwt.getClaim("agentId");
            agentId = value == null ? null : Long.valueOf(value.toString());
        }
        return new LoginUser(userId, username, nickname, agentId, splitScopes(jwt.getClaimAsString("scope")));
    }

    private Set<String> splitScopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                result.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }
}
