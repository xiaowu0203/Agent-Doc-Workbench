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

/** 仅接收已由当前OIDC模式验证签名、issuer及时间的JWT；不从请求头读取业务身份。 */
@Component
@RequiredArgsConstructor
public class OidcHumanAuthenticationConverter implements Converter<Jwt, ResolvedHumanAuthentication> {
    private final IdentityBindingService bindings;
    private final PlatformRoleService roles;

    @Override
    public ResolvedHumanAuthentication convert(Jwt jwt) {
        if (!OidcHumanTokenUtils.isHumanAccess(jwt)) {
            throw invalidToken();
        }
        try {
            Long userId = bindings.resolve(jwt.getClaimAsString("iss"), jwt.getSubject());
            return new ResolvedHumanAuthentication(jwt, userId, roles.listRoleKeys(userId));
        } catch (BusinessException error) {
            throw invalidToken();
        }
    }

    private OAuth2AuthenticationException invalidToken() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
    }
}
