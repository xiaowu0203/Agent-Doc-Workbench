package com.agentdoc.common.security;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.HumanAuthorizationException;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.utils.AuthUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/** 平台角色的当前人类复核规则；可信投影只作快速拒绝，权威结果来自Auth。 */
public final class PlatformRoleVerifier {
    private PlatformRoleVerifier() { }

    public static boolean hasRole(AuthIdentityFeign identity, String roleKey) {
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
