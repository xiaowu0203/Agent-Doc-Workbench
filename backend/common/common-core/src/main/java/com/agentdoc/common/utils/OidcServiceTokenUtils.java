package com.agentdoc.common.utils;

import com.agentdoc.common.constant.IdentityTokenConstant;
import com.agentdoc.common.constant.JwtConstant;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 仅在当前OIDC issuer独立验签后分类SERVICE；不做用户映射或资源授权。 */
public final class OidcServiceTokenUtils {
    private OidcServiceTokenUtils() { }

    public static Jwt requireService(Jwt jwt, Instant now, Set<String> registeredClients) {
        if (jwt == null) { throw invalid(); }
        try {
            String client = jwt.getClaimAsString("azp");
            String actor = jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE);
            String scope = jwt.getClaimAsString(JwtConstant.CLAIM_SCOPE);
            Set<String> scopes = scope == null ? Set.of() : Arrays.stream(scope.strip().split("\\s+")).collect(Collectors.toSet());
            if (!"RS256".equals(jwt.getHeaders().get("alg")) || !"JWT".equals(jwt.getHeaders().get("typ"))
                    || !(jwt.getHeaders().get("kid") instanceof String kid) || kid.isBlank()
                    || !"Bearer".equals(jwt.getClaimAsString("typ"))
                    || client == null || !client.matches(IdentityTokenConstant.SERVICE_CLIENT_PATTERN)
                    || IdentityTokenConstant.BFF_CLIENT.equals(client) || !registeredClients.contains(client)
                    || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || jwt.getAudience() == null || !jwt.getAudience().contains(IdentityTokenConstant.SERVICE_AUDIENCE)
                    || jwt.getAudience().contains(IdentityTokenConstant.HUMAN_AUDIENCE)
                    || !scopes.contains(IdentityTokenConstant.SERVICE_SCOPE) || scopes.contains(IdentityTokenConstant.HUMAN_SCOPE)
                    || (actor != null && !JwtConstant.ACTOR_SERVICE.equals(actor))
                    || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null || jwt.getIssuedAt().getEpochSecond() <= 0
                    || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    || Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(IdentityTokenConstant.SERVICE_MAX_LIFETIME) > 0
                    || jwt.getIssuedAt().isAfter(now.plus(IdentityTokenConstant.CLOCK_SKEW))
                    || jwt.getExpiresAt().isBefore(now.minus(IdentityTokenConstant.CLOCK_SKEW))
                    || jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now.plus(IdentityTokenConstant.CLOCK_SKEW))) {
                throw invalid();
            }
            return jwt;
        } catch (IllegalArgumentException | ClassCastException error) { throw invalid(); }
    }

    private static BadJwtException invalid() { return new BadJwtException("无效的OIDC服务凭据"); }
}
