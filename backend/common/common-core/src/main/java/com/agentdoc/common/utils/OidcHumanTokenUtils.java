package com.agentdoc.common.utils;

import com.agentdoc.common.constant.JwtConstant;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;

/** OIDC人类Access的用途约束；调用前必须独立完成签名、issuer和时间验证。 */
public final class OidcHumanTokenUtils {
    private OidcHumanTokenUtils() { }

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
            return false;
        }
    }
}
