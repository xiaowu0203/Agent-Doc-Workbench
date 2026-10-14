package com.agentdoc.common.security;

import static com.agentdoc.common.constant.OnlineCapabilityConstant.*;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 专用头校验器，不建立用户、Agent 或离线 Worker 上下文。 */
public class OnlineCapabilityVerifier {
    private final JwtDecoder decoder;
    private final SecurityVerifyProperties properties;
    public OnlineCapabilityVerifier(JwtDecoder decoder, SecurityVerifyProperties properties) {
        this.decoder = decoder;
        this.properties = properties;
    }

    public Jwt verify(String token, OnlineCapabilityPurpose purpose, String experimentId, List<String> taskIds) {
        try {
            if (!properties.isOnlineCapabilityEnabled() || token == null || token.isBlank()
                    || token.length() > MAX_TOKEN_LENGTH || purpose == null) { throw denied(); }
            Jwt jwt = decoder.decode(token);
            Instant now = Instant.now();
            Object schema = jwt.getClaim(SCHEMA);
            if (!"RS256".equals(jwt.getHeaders().get("alg"))
                    || !properties.getOnlineCapabilityIssuer().equals(jwt.getClaimAsString("iss"))
                    || !JwtConstant.EVALUATION_SERVICE.equals(jwt.getSubject())
                    || !JwtConstant.EVALUATION_SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SERVICE))
                    || !JwtConstant.ACTOR_SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE))
                    || !JwtConstant.SCOPE_SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SCOPE))
                    || !List.of(purpose.audience()).equals(jwt.getAudience())
                    || !purpose.name().equals(jwt.getClaimAsString(PURPOSE))
                    || !(schema instanceof Number)
                    || !String.valueOf(OnlineProtocolUtils.SCHEMA_VERSION).equals(String.valueOf(schema))
                    || jwt.getId() == null || jwt.getId().isBlank()
                    || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(now)
                    || jwt.getNotBefore() == null || jwt.getNotBefore().isAfter(now)
                    || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(now)
                    || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    || Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(Duration.ofSeconds(TTL_SECONDS)) > 0
                    || jwt.hasClaim(JwtConstant.CLAIM_AGENT_ACTIONS) || jwt.hasClaim(JwtConstant.CLAIM_WORKER_ACTIONS)
                    || jwt.hasClaim(JwtConstant.CLAIM_PLATFORM_ROLES)) { throw denied(); }
            for (String field : List.of(EXPERIMENT_ID, JwtConstant.CLAIM_SPACE_ID, AUTHORIZED_BY)) {
                Object value = jwt.getClaim(field);
                if (!(value instanceof String text)) { throw denied(); }
                OnlineProtocolUtils.id(text);
            }
            String manifest = jwt.getClaimAsString(MANIFEST_HASH);
            if (manifest == null || !manifest.matches("[0-9a-f]{64}")) { throw denied(); }
            if (experimentId != null && !experimentId.equals(jwt.getClaimAsString(EXPERIMENT_ID))) { throw denied(); }
            if (purpose == OnlineCapabilityPurpose.ONLINE_CONTROL) {
                if (taskIds != null && !taskIds.isEmpty() || jwt.hasClaim(TASK_SET_HASH)) { throw denied(); }
            } else if (!OnlineCapabilityUtils.taskSetHash(taskIds).equals(jwt.getClaimAsString(TASK_SET_HASH))) {
                throw denied();
            }
            return jwt;
        } catch (RuntimeException exception) { throw denied(); }
    }

    /** WAIT 不进入普通 SecurityContext，仅限一个已接受 Task 的排队与交换。 */
    public Jwt verifyWait(String token, String taskId) {
        try {
            if (!properties.isOnlineCapabilityEnabled() || token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) { throw denied(); }
            Jwt jwt = decoder.decode(token);
            Instant now = Instant.now(); Object schema = jwt.getClaim(BINDING_SCHEMA);
            if (!"RS256".equals(jwt.getHeaders().get("alg")) || !properties.getOnlineCapabilityIssuer().equals(jwt.getClaimAsString("iss"))
                    || !List.of(WAIT_AUDIENCE).equals(jwt.getAudience()) || !WAIT_PURPOSE.equals(jwt.getClaimAsString(PURPOSE))
                    || !WAIT_SCOPE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SCOPE))
                    || !JwtConstant.ACTOR_SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE))
                    || !TASK_SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SERVICE))
                    || !taskId.equals(jwt.getSubject()) || !taskId.equals(jwt.getClaimAsString(JwtConstant.CLAIM_TASK_ID))
                    || !(schema instanceof Number) || !"2".equals(String.valueOf(schema))
                    || jwt.getId() == null || jwt.getId().isBlank() || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(now)
                    || jwt.getNotBefore() == null || jwt.getNotBefore().isAfter(now) || jwt.getExpiresAt() == null
                    || !jwt.getExpiresAt().isAfter(now) || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    || Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(Duration.ofSeconds(TTL_SECONDS)) > 0
                    || jwt.hasClaim(JwtConstant.CLAIM_AGENT_ACTIONS) || jwt.hasClaim(JwtConstant.CLAIM_WORKER_ACTIONS)
                    || jwt.hasClaim(JwtConstant.CLAIM_PLATFORM_ROLES) || jwt.hasClaim(SLOT_GENERATION) || jwt.hasClaim(SLOT_PERMIT_HASH)) { throw denied(); }
            for (String field : List.of(EXPERIMENT_ID, ASSIGNMENT_ID, JwtConstant.CLAIM_SPACE_ID, JwtConstant.CLAIM_TASK_ID)) {
                Object claim = jwt.getClaim(field); if (!(claim instanceof String value)) { throw denied(); } OnlineProtocolUtils.id(value);
            }
            for (String field : List.of(BINDING_HASH, MANIFEST_HASH)) {
                String hash = jwt.getClaimAsString(field); if (hash == null || !hash.matches("[0-9a-f]{64}")) { throw denied(); }
            }
            return jwt;
        } catch (RuntimeException invalid) { throw denied(); }
    }

    private JwtException denied() { return new JwtException("线上窄凭证用途、有效期或范围校验失败"); }
}
