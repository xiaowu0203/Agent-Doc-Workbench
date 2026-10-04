package com.agentdoc.common.security;

import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.feign.dto.TaskRecoveryIdentityDTO;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 恢复入口独立 verifier：不建立人类、Agent 或 Worker 身份上下文。 */
public class TaskRecoveryVerifier {
    private final JwtDecoder decoder;
    private final SecurityVerifyProperties properties;

    public TaskRecoveryVerifier(JwtDecoder decoder, SecurityVerifyProperties properties) {
        this.decoder = decoder;
        this.properties = properties;
    }

    /** 验证固定 audience/purpose/动作/时间及路径资源，返回用于接收方再次核验的声明。 */
    public Jwt verify(String token, String action, String resourceId) {
        try { return verifyClaims(token, action, resourceId); }
        catch (RuntimeException exception) { throw denied(); }
    }

    private Jwt verifyClaims(String token, String action, String resourceId) {
        if (!properties.isTaskRecoveryEnabled() || token == null || token.isBlank()
                || token.length() > TaskRecoveryConstant.MAX_PROOF_LENGTH) { throw denied(); }
        Jwt jwt = decoder.decode(token);
        boolean draft = TaskRecoveryConstant.FINALIZE.equals(action) || TaskRecoveryConstant.DISCARD.equals(action);
        if (!draft && !Set.of(TaskRecoveryConstant.QUERY, TaskRecoveryConstant.CANCEL).contains(action)) { throw denied(); }
        long maxTtl = draft ? TaskRecoveryConstant.DRAFT_TTL_SECONDS : TaskRecoveryConstant.A2A_TTL_SECONDS;
        Instant now = Instant.now();
        if (!"RS256".equals(jwt.getHeaders().get("alg"))
                || !properties.getTaskRecoveryIssuer().equals(jwt.getClaimAsString("iss"))
                || !TaskRecoveryConstant.SERVICE.equals(jwt.getSubject())
                || !JwtConstant.ACTOR_SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE))
                || !JwtConstant.SCOPE_SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SCOPE))
                || !TaskRecoveryConstant.SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SERVICE))
                || !List.of(draft ? TaskRecoveryConstant.DRAFT_AUDIENCE : TaskRecoveryConstant.A2A_AUDIENCE)
                .equals(jwt.getAudience())
                || !(draft ? TaskRecoveryConstant.DRAFT_PURPOSE : TaskRecoveryConstant.A2A_PURPOSE)
                .equals(jwt.getClaimAsString(TaskRecoveryConstant.PURPOSE))
                || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(now)
                || jwt.getNotBefore() == null || jwt.getNotBefore().isAfter(now)
                || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(now)
                || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                || Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds() > maxTtl
                || jwt.hasClaim(JwtConstant.CLAIM_AGENT_ACTIONS) || jwt.hasClaim(JwtConstant.CLAIM_WORKER_ACTIONS)
                || !resourceId.equals(jwt.getClaimAsString(draft ? JwtConstant.CLAIM_TASK_ID
                : TaskRecoveryConstant.A2A_TASK_ID))) { throw denied(); }
        requireText(jwt, TaskRecoveryConstant.SOURCE_JTI);
        requireText(jwt, TaskRecoveryConstant.A2A_TASK_ID);
        requireText(jwt, "jti");
        try { UUID.fromString(jwt.getClaimAsString(TaskRecoveryConstant.RECOVERY_ID)); }
        catch (RuntimeException exception) { throw denied(); }
        List<String> actions = jwt.getClaimAsStringList(TaskRecoveryConstant.ACTIONS);
        if (actions == null || !actions.contains(action) || actions.size() != Set.copyOf(actions).size()) { throw denied(); }
        if (draft) {
            String terminal = jwt.getClaimAsString(TaskRecoveryConstant.REMOTE_STATUS);
            boolean finalize = TaskRecoveryConstant.FINALIZE.equals(action);
            if (!List.of(action).equals(actions)
                    || !TaskExecutionMode.LIVE.name().equals(jwt.getClaimAsString(JwtConstant.CLAIM_EXECUTION_MODE))
                    || !(finalize ? "COMPLETED".equals(terminal) : Set.of("FAILED", "CANCELED").contains(terminal))) {
                throw denied();
            }
        } else if (!Set.of(TaskRecoveryConstant.QUERY, TaskRecoveryConstant.CANCEL).containsAll(actions)
                || !actions.contains(TaskRecoveryConstant.QUERY) || jwt.hasClaim(TaskRecoveryConstant.REMOTE_STATUS)) {
            throw denied();
        }
        for (String field : List.of(JwtConstant.CLAIM_TASK_ID, JwtConstant.CLAIM_AGENT_ID, JwtConstant.CLAIM_SPACE_ID,
                JwtConstant.CLAIM_DOCUMENT_ID, JwtConstant.CLAIM_INPUT_SNAPSHOT_SCHEMA_VERSION)) {
            try { if (Long.parseLong(jwt.getClaimAsString(field)) <= 0) { throw denied(); } }
            catch (RuntimeException exception) { throw denied(); }
        }
        // 新建文档的有效初始快照为 v0，不能把版本号当成正数资源 ID。
        if (Long.parseLong(jwt.getClaimAsString(JwtConstant.CLAIM_DOCUMENT_VERSION_SNAPSHOT)) < 0) { throw denied(); }
        for (String field : List.of(JwtConstant.CLAIM_DOCUMENT_CONTENT_SHA256, JwtConstant.CLAIM_INPUT_SNAPSHOT_HASH)) {
            if (!requireText(jwt, field).matches("[a-f0-9]{64}")) { throw denied(); }
        }
        String derivation = jwt.getClaimAsString(JwtConstant.CLAIM_DERIVATION_REQUEST_HASH);
        if (derivation != null && !derivation.matches("[a-f0-9]{64}")) { throw denied(); }
        if (!Set.of("LIVE", "ISOLATED").contains(jwt.getClaimAsString(JwtConstant.CLAIM_EXECUTION_MODE))) { throw denied(); }
        return jwt;
    }

    /** 草稿路径先解码用途，再按唯一动作执行完整验证；不接受请求中的动作。 */
    public Jwt verifyDraft(String token, Long taskId) {
        try {
            if (!properties.isTaskRecoveryEnabled() || token == null || token.length() > TaskRecoveryConstant.MAX_PROOF_LENGTH) { throw denied(); }
            Jwt parsed = decoder.decode(token);
            String action = "COMPLETED".equals(parsed.getClaimAsString(TaskRecoveryConstant.REMOTE_STATUS))
                    ? TaskRecoveryConstant.FINALIZE : TaskRecoveryConstant.DISCARD;
            return verify(token, action, String.valueOf(taskId));
        } catch (RuntimeException exception) { throw denied(); }
    }

    /** 逐字段匹配接收方已有冻结输入；可选派生身份仅在双方都为空时省略。 */
    public void requireIdentity(Jwt jwt, TaskRecoveryIdentityDTO identity) {
        identity.toClaims().forEach((field, expected) -> {
            Object actual = jwt.getClaim(field);
            if (JwtConstant.CLAIM_DERIVATION_REQUEST_HASH.equals(field) && expected == null && actual == null) { return; }
            if (expected == null || actual == null || !String.valueOf(expected).equals(String.valueOf(actual))) { throw denied(); }
        });
    }

    private String requireText(Jwt jwt, String field) {
        String value = jwt.getClaimAsString(field);
        if (value == null || value.isBlank()) { throw denied(); }
        return value;
    }

    private JwtException denied() { return new JwtException("恢复凭证用途、动作或资源校验失败"); }
}
