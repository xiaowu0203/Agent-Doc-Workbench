package com.agentdoc.auth.service;

import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;

/** 用户级人类认证撤销；不影响服务证明、任务能力或其他SSO应用。 */
@Service
@RequiredArgsConstructor
public class HumanAuthenticationService {

    private final UserMapper users;
    private final Clock clock;

    /** 与调用方的账号/绑定变更共同提交；SQL保证并发及时间回退时边界不下降。 */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long userId) {
        long notBefore = clock.instant().getEpochSecond() + JwtConstant.OIDC_CLOCK_SKEW_SECONDS;
        if (users.advanceAuthenticationVersion(userId, notBefore) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户认证状态已改变");
        }
    }

    /** 仅消费已验签原始JWT的auth_time，刷新产生的新iat不能跨过撤销边界。 */
    public void requireCurrentOidcAuthentication(UserEntity user, Jwt jwt) {
        if (user.getAuthVersion() == null || user.getAuthVersion() < 0
                || user.getOidcAuthNotBefore() == null || user.getOidcAuthNotBefore() < 0) {
            throw new AuthenticationServiceException("用户认证状态不可用");
        }
        Object claim = jwt.getClaims().get("auth_time");
        if (!(claim instanceof Long || claim instanceof Integer)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        long authTime = ((Number) claim).longValue();
        long latest = clock.instant().getEpochSecond() + JwtConstant.OIDC_CLOCK_SKEW_SECONDS;
        if (authTime <= 0 || authTime > latest || authTime <= user.getOidcAuthNotBefore()
                || jwt.getIssuedAt() == null || jwt.getIssuedAt().getEpochSecond() > latest
                || authTime > jwt.getIssuedAt().getEpochSecond() + JwtConstant.OIDC_CLOCK_SKEW_SECONDS) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
