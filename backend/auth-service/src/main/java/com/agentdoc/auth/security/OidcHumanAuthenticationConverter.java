package com.agentdoc.auth.security;

import com.agentdoc.auth.service.IdentityBindingService;
import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.common.utils.OidcHumanTokenUtils;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.security.ResolvedHumanAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 仅接收已由当前OIDC模式验证签名、issuer及时间的JWT；不从请求头读取业务身份。
 * <p>
 * OIDC模式JWT转换器，将经过密码学校验的OIDC JWT转换为平台内部人类认证对象；
 * 依赖 IdentityBindingService 根据 iss + sub 查找绑定的平台userId，再加载该用户平台角色。
 * 只在OIDC模式的JwtAuthenticationConverter链路使用，local模式不走该类。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OidcHumanAuthenticationConverter implements Converter<Jwt, ResolvedHumanAuthentication> {

    private final IdentityBindingService bindings;
    private final PlatformRoleService roles;

    /**
     * 转换流程：
     * 1. 业务声明校验，确认是人类访问令牌
     * 2. 使用 jwt.iss + jwt.sub 查询外部身份绑定记录，映射为平台userId
     * 3. 查询该用户的平台角色列表，构造 ResolvedHumanAuthentication
     * @param jwt 已经完成签名、issuer、时间校验的JWT
     * @return 平台内部人类身份认证凭证
     */
    @Override
    public ResolvedHumanAuthentication convert(Jwt jwt) {
        if (!OidcHumanTokenUtils.isHumanAccess(jwt)) {
            throw invalidToken();
        }
        try {
            // 根据外部IdP的issuer和subject，解析绑定的平台用户ID
            Long userId = bindings.resolve(jwt.getClaimAsString("iss"), jwt.getSubject());
            // 查询该用户绑定的平台角色，组装内部认证对象
            return new ResolvedHumanAuthentication(jwt, userId, roles.listRoleKeys(userId));
        } catch (BusinessException error) {
            // 找不到绑定记录、绑定已撤销等业务异常，统一转为 invalid_token
            throw invalidToken();
        }
    }

    /**
     * 构造标准OAuth2令牌异常，由AuthenticationEntryPoint捕获输出401
     */
    private OAuth2AuthenticationException invalidToken() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
    }
}
