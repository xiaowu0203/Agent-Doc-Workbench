package com.agentdoc.auth.security;

import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.common.constant.IdentityTokenConstant;
import com.agentdoc.common.security.ResolvedHumanAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** 仅消费本地身份验签器已验证的HUMAN凭据；每次从数据库复核状态、版本和角色。 */
@Component
@RequiredArgsConstructor
public class LocalHumanAuthenticationConverter implements Converter<Jwt, ResolvedHumanAuthentication> {
    private final UserMapper users;
    private final PlatformRoleService roles;

    @Override
    public ResolvedHumanAuthentication convert(Jwt jwt) {
        try {
            Long userId = Long.valueOf(jwt.getSubject());
            var user = users.selectById(userId);
            if (user == null || !UserStatus.isEnabled(user.getStatus())) { throw invalidToken(); }
            if (user.getAuthVersion() == null || user.getAuthVersion() < 0) {
                throw new AuthenticationServiceException("用户认证状态不可用");
            }
            Number version = jwt.getClaim(IdentityTokenConstant.CLAIM_AUTH_VERSION);
            if (version.longValue() != user.getAuthVersion()) { throw invalidToken(); }
            return new ResolvedHumanAuthentication(jwt, userId, roles.listRoleKeys(userId));
        } catch (DataAccessException error) {
            throw new AuthenticationServiceException("用户认证状态不可用");
        }
    }

    private static OAuth2AuthenticationException invalidToken() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
    }
}
