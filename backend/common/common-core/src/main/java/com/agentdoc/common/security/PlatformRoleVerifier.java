package com.agentdoc.common.security;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.HumanAuthorizationException;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.utils.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/** 平台角色的当前人类复核规则；可信投影只作快速拒绝，权威结果来自Auth。 */
@RequiredArgsConstructor
public final class PlatformRoleVerifier {
    private final AuthIdentityFeign identity;

    /** 要求当前人类拥有指定平台角色，身份及依赖故障沿用hasRole的分类。 */
    public void requireRole(String roleKey) {
        if (!hasRole(roleKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少平台角色：" + roleKey);
        }
    }

    public boolean hasRole(String roleKey) {
        if (roleKey == null || !AuthUtils.hasPlatformRole(roleKey)) {
            return false;
        }
        Result<Void> result;
        try {
            result = identity.checkPlatformRole("Bearer " + AuthUtils.currentJwt().getTokenValue(), roleKey);
        } catch (BadCredentialsException error) {
            throw new HumanAuthorizationException(ErrorCode.UNAUTHORIZED);
        } catch (AccessDeniedException error) {
            return false;
        } catch (RuntimeException error) {
            throw new HumanAuthorizationException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        if (result != null && result.code() == ErrorCode.SUCCESS.getCode()) {
            return true;
        }
        if (result != null && result.code() == ErrorCode.FORBIDDEN.getCode()) {
            return false;
        }
        if (result != null && (result.code() == ErrorCode.UNAUTHORIZED.getCode()
                || result.code() == ErrorCode.TOKEN_EXPIRED.getCode())) {
            throw new HumanAuthorizationException(ErrorCode.UNAUTHORIZED);
        }
        throw new HumanAuthorizationException(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
