package com.agentdoc.common.security;

import com.agentdoc.common.constant.JwtConstant;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** 已验签且经可信Auth绑定解析的人类身份；不得由请求头或未验证声明构造。 */
public final class ResolvedHumanAuthentication extends AbstractAuthenticationToken {
    private final Jwt jwt;
    private final Long userId;
    private final List<String> platformRoles;

    public ResolvedHumanAuthentication(Jwt jwt, Long userId, List<String> platformRoles) {
        super(List.of());
        if (jwt == null || userId == null || userId <= 0 || platformRoles == null
                || (jwt.hasClaim(JwtConstant.CLAIM_ACTOR_TYPE)
                && !JwtConstant.ACTOR_HUMAN.equals(jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE)))) {
            throw new IllegalArgumentException("无效的人类身份投影");
        }
        this.jwt = jwt;
        this.userId = userId;
        this.platformRoles = List.copyOf(platformRoles);
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return ""; }
    @Override public Jwt getPrincipal() { return jwt; }
    @Override public String getName() { return userId.toString(); }
    public Long getUserId() { return userId; }
    public boolean hasPlatformRole(String role) { return platformRoles.contains(role); }
}
