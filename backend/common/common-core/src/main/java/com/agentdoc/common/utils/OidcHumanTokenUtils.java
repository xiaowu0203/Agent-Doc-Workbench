package com.agentdoc.common.utils;

import com.agentdoc.common.constant.JwtConstant;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;

/**
 * OIDC人类Access的用途约束；调用前必须独立完成签名、issuer和时间验证。
 * <p>
 * 用于校验OIDC Access Token的业务声明约束，判断该令牌是否属于合法的人类访问令牌；
 * ⚠️ 重要前置：这个方法**不做签名、iss、exp时间校验**，这些必须由上层JwtDecoder提前校验完成。
 * 仅在 {@link OidcRemoteHumanAuthenticationConverter} 内使用，过滤掉能力令牌、其他客户端/用途的令牌。
 * </p>
 */
public final class OidcHumanTokenUtils {

    private OidcHumanTokenUtils() { }

    /**
     * 判断Jwt是否符合OIDC人类访问令牌声明规范
     * 校验项：exp/iat/sub/aud非空、aud、azp、typ、scope、actor_type约束
     * @param jwt 已经验签、时间合法的JWT对象
     * @return true = 满足人类访问令牌声明约束
     */
    public static boolean isHumanAccess(Jwt jwt) {
        if (jwt == null) { return false; }
        try {
            String actor = jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE);
            String scope = jwt.getClaimAsString("scope");
            return jwt.getExpiresAt() != null && jwt.getIssuedAt() != null && jwt.getSubject() != null
                    && jwt.getAudience() != null && jwt.getAudience().contains(JwtConstant.OIDC_HUMAN_AUDIENCE)
                    && JwtConstant.OIDC_HUMAN_CLIENT.equals(jwt.getClaimAsString("azp"))
                    && "Bearer".equals(jwt.getClaimAsString("typ"))
                    && scope != null && Arrays.asList(scope.split("\\s+")).contains(JwtConstant.OIDC_HUMAN_SCOPE)
                    && (actor == null || JwtConstant.ACTOR_HUMAN.equals(actor));
        } catch (IllegalArgumentException | ClassCastException error) {
            // 声明类型不匹配、取值异常直接判定不是合法人类令牌
            return false;
        }
    }
}
