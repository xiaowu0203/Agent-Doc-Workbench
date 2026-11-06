package com.agentdoc.common.security;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import com.agentdoc.common.utils.OidcHumanTokenUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

/** 仅以已验签人类Bearer查询本人身份；业务授权仍由当前业务服务执行。 */
public final class OidcRemoteHumanAuthenticationConverter implements Converter<Jwt, ResolvedHumanAuthentication> {
    private final AuthIdentityFeign identity;

    public OidcRemoteHumanAuthenticationConverter(AuthIdentityFeign identity) {
        this.identity = identity;
    }

    @Override
    public ResolvedHumanAuthentication convert(Jwt jwt) {
        if (!OidcHumanTokenUtils.isHumanAccess(jwt)) { throw new BadCredentialsException("无效的人类令牌"); }
        try {
            Result<IdentitySelfVO> result = identity.currentIdentity("Bearer " + jwt.getTokenValue());
            if (result == null || result.code() >= 50000) { throw new AuthenticationServiceException("身份服务不可用"); }
            if (result.code() != 0) { throw new BadCredentialsException("用户身份不可用"); }
            IdentitySelfVO resolved = result.data();
            if (resolved == null || resolved.userId() == null || resolved.userId() <= 0 || resolved.platformRoles() == null) {
                throw new AuthenticationServiceException("身份服务返回无效结果");
            }
            return new ResolvedHumanAuthentication(jwt, resolved.userId(), resolved.platformRoles());
        } catch (AuthenticationException error) {
            throw error;
        } catch (RuntimeException error) {
            // Feign异常可能包含请求和凭据，不拼接错误或保留原始cause。
            throw new AuthenticationServiceException("身份服务不可用");
        }
    }
}
