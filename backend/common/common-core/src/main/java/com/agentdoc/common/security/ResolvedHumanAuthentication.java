package com.agentdoc.common.security;

import com.agentdoc.common.constant.JwtConstant;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * 已验签且经可信Auth绑定解析的人类身份；不得由请求头或未验证声明构造。
 * <p>
 * Spring Security认证对象，代表经过完整校验的登录人类用户身份；
 * 仅在OIDC模式下由 {@link OidcRemoteHumanAuthenticationConverter} 构建，
 * 禁止直接从原始请求头、未校验的JWT声明手动实例化。
 * </p>
 */
public final class ResolvedHumanAuthentication extends AbstractAuthenticationToken {

    private final Jwt jwt;
    private final Long userId;
    private final List<String> platformRoles;

    /**
     * @param jwt 已通过OIDC解码器验签的原始JWT
     * @param userId 平台内部用户ID
     * @param platformRoles 用户平台角色列表
     */
    public ResolvedHumanAuthentication(Jwt jwt, Long userId, List<String> platformRoles) {
        super(List.of());
        // 构造时强校验：非空、userId合法、actor_type必须是人类，拒绝能力令牌等非人类令牌
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

    /**
     * 凭证不对外暴露，返回空字符串
     */
    @Override
    public Object getCredentials() {
        return "";
    }

    /**
     * Principal为原始已验签JWT
     */
    @Override
    public Jwt getPrincipal() {
        return jwt;
    }

    /**
     * Security上下文默认名称：平台userId字符串
     */
    @Override
    public String getName() {
        return userId.toString();
    }

    public Long getUserId() {
        return userId;
    }

    /**
     * 快捷判断是否拥有指定平台角色
     */
    public boolean hasPlatformRole(String role) {
        return platformRoles.contains(role);
    }
}
