package com.agentdoc.common.utils;

import com.agentdoc.common.constant.IdentityTokenConstant;
import com.agentdoc.common.constant.JwtConstant;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 仅在独立身份密钥验签后使用；声明检查不能替代签名校验。 */
public final class LocalIdentityTokenUtils {
    private LocalIdentityTokenUtils() { }

    /** 检查正式local人类声明；拒绝旧scope=user、服务及任何业务Capability用途。 */
    public static Jwt requireHuman(Jwt jwt, Instant now) {
        requireCommon(jwt, now, IdentityTokenConstant.HUMAN_JOSE_TYPE, IdentityTokenConstant.HUMAN_PURPOSE,
                IdentityTokenConstant.HUMAN_AUDIENCE, IdentityTokenConstant.HUMAN_SCOPE,
                JwtConstant.ACTOR_HUMAN, IdentityTokenConstant.HUMAN_MAX_LIFETIME);
        if (!IdentityTokenConstant.BFF_CLIENT.equals(jwt.getClaims().get("azp"))
                || !canonicalUserId(jwt.getSubject()) || !canonicalUuid(jwt.getClaimAsString(IdentityTokenConstant.CLAIM_SESSION_ID))
                || authenticationVersion(jwt) == null) {
            throw invalid();
        }
        return jwt;
    }

    /** 验证服务证明并限制到消费端登记client；不读取任何用户版本或人类身份。 */
    public static Jwt requireService(Jwt jwt, Instant now, Set<String> allowedClients) {
        requireCommon(jwt, now, IdentityTokenConstant.SERVICE_JOSE_TYPE, IdentityTokenConstant.SERVICE_PURPOSE,
                IdentityTokenConstant.SERVICE_AUDIENCE, IdentityTokenConstant.SERVICE_SCOPE,
                JwtConstant.ACTOR_SERVICE, IdentityTokenConstant.SERVICE_MAX_LIFETIME);
        String client = jwt.getSubject();
        if (client == null || !client.matches(IdentityTokenConstant.SERVICE_CLIENT_PATTERN)
                || IdentityTokenConstant.BFF_CLIENT.equals(client) || !allowedClients.contains(client)
                || !client.equals(jwt.getClaims().get("azp")) || !canonicalUuid(jwt.getId())
                || jwt.hasClaim(IdentityTokenConstant.CLAIM_AUTH_VERSION) || jwt.hasClaim(IdentityTokenConstant.CLAIM_SESSION_ID)
                || jwt.hasClaim(JwtConstant.CLAIM_PLATFORM_ROLES) || jwt.hasClaim("userId")) {
            throw invalid();
        }
        return jwt;
    }

    /** 版本必须是签名的非负整数，不把缺省/字符串版本解释为0。 */
    public static Long authenticationVersion(Jwt jwt) {
        Object value = jwt.getClaims().get(IdentityTokenConstant.CLAIM_AUTH_VERSION);
        return (value instanceof Long || value instanceof Integer) && ((Number) value).longValue() >= 0
                ? ((Number) value).longValue() : null;
    }

    private static void requireCommon(Jwt jwt, Instant now, String type, String purpose, String audience,
                                      String scope, String actor, Duration maxLifetime) {
        if (jwt == null || !"RS256".equals(jwt.getHeaders().get("alg"))
                || !type.equals(jwt.getHeaders().get("typ"))
                || !(jwt.getHeaders().get("kid") instanceof String kid) || !kid.startsWith(IdentityTokenConstant.LOCAL_KEY_PREFIX)
                || !IdentityTokenConstant.LOCAL_ISSUER.equals(jwt.getClaimAsString("iss"))
                || !purpose.equals(jwt.getClaims().get(IdentityTokenConstant.CLAIM_PURPOSE))
                || !actor.equals(jwt.getClaims().get(JwtConstant.CLAIM_ACTOR_TYPE))
                || !hasOnlyAudience(jwt, audience) || !hasOnlyScope(jwt, scope)
                || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                || jwt.getIssuedAt().getEpochSecond() <= 0 || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                || Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(maxLifetime) > 0
                || jwt.getIssuedAt().isAfter(now.plus(IdentityTokenConstant.CLOCK_SKEW))
                || jwt.getExpiresAt().isBefore(now.minus(IdentityTokenConstant.CLOCK_SKEW))
                || (jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now.plus(IdentityTokenConstant.CLOCK_SKEW)))) {
            throw invalid();
        }
    }

    private static boolean hasOnlyScope(Jwt jwt, String scope) {
        Object value = jwt.getClaims().get(JwtConstant.CLAIM_SCOPE);
        return value instanceof String text && Set.of(scope).equals(Arrays.stream(text.strip().split("\\s+"))
                .collect(Collectors.toSet()));
    }

    private static boolean hasOnlyAudience(Jwt jwt, String audience) {
        Object value = jwt.getClaims().get("aud");
        return audience.equals(value) || value instanceof Collection<?> values
                && values.size() == 1 && values.contains(audience);
    }

    private static boolean canonicalUserId(String value) {
        try { return value != null && Long.parseLong(value) > 0 && Long.toString(Long.parseLong(value)).equals(value); }
        catch (NumberFormatException error) { return false; }
    }

    private static boolean canonicalUuid(String value) {
        try { return value != null && UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException error) { return false; }
    }

    private static BadJwtException invalid() { return new BadJwtException("无效的本地身份凭据"); }
}
