package com.agentdoc.auth.service;

import com.agentdoc.auth.config.TaskRecoverySigningProperties;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.TaskRecoveryIssueDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 只在专用签发路径核验已过期历史证明，绝不改变普通 JwtDecoder。 */
@Slf4j
@Service
public class TaskRecoveryIssuanceService {
    private final JwtService jwtService;
    private final TaskRecoverySigningProperties properties;
    private final NimbusJwtDecoder historicalDecoder;

    public TaskRecoveryIssuanceService(JwtService jwtService, TaskRecoverySigningProperties properties) {
        this.jwtService = jwtService;
        this.properties = properties;
        historicalDecoder = NimbusJwtDecoder.withPublicKey(jwtService.getPublicKey()).build();
        // Nimbus 仍严格验证 RS256 签名；时间和声明在下面专门核验，只有 exp 允许过去。
        historicalDecoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
    }

    /** 机器认证、历史证明和冻结身份全部通过后，签发固定用途/有效期的窄凭证。 */
    public String issue(String machineKey, TaskRecoveryIssueDTO request, boolean draftFinalization) {
        requireMachineIdentity(machineKey);
        try {
            Jwt source = requireHistoricalProof(request);
            List<String> actions;
            if (draftFinalization) {
                if (!TaskExecutionMode.LIVE.name().equals(request.identity().executionMode())
                        || !source.getClaimAsStringList(JwtConstant.CLAIM_AGENT_ACTIONS)
                        .contains(JwtConstant.ACTION_WRITE_DRAFT)) {
                    throw denied();
                }
                actions = switch (Objects.toString(request.remoteTerminalStatus(), "")) {
                    case "COMPLETED" -> List.of(TaskRecoveryConstant.FINALIZE);
                    case "FAILED", "CANCELED" -> List.of(TaskRecoveryConstant.DISCARD);
                    default -> throw denied();
                };
            } else {
                if (request.remoteTerminalStatus() != null) { throw denied(); }
                actions = request.cancelRequested()
                        ? List.of(TaskRecoveryConstant.QUERY, TaskRecoveryConstant.CANCEL)
                        : List.of(TaskRecoveryConstant.QUERY);
            }
            String token = jwtService.createTaskRecoveryToken(source, request, draftFinalization, actions);
            log.info("Task 恢复签发成功，service={}，taskId={}，recoveryId={}，purpose={}",
                    TaskRecoveryConstant.SERVICE, request.identity().taskId(), request.recoveryId(),
                    draftFinalization ? TaskRecoveryConstant.DRAFT_PURPOSE : TaskRecoveryConstant.A2A_PURPOSE);
            return token;
        } catch (RuntimeException exception) {
            // 不打印请求、证明或底层 JWT 异常消息。
            log.info("Task 恢复签发拒绝，service={}，reason=SOURCE_CAPABILITY_INVALID",
                    TaskRecoveryConstant.SERVICE);
            throw denied();
        }
    }

    private void requireMachineIdentity(String supplied) {
        byte[] current = Objects.toString(properties.getMachineKey(), "").getBytes(StandardCharsets.UTF_8);
        if (current.length < TaskRecoveryConstant.MIN_MACHINE_KEY_BYTES) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "RECOVERY_ISSUANCE_FAILED");
        }
        byte[] candidate = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        boolean accepted = MessageDigest.isEqual(current, candidate);
        Instant rotated = properties.getKeyRotatedAt();
        Instant now = Instant.now();
        byte[] previous = Objects.toString(properties.getPreviousMachineKey(), "").getBytes(StandardCharsets.UTF_8);
        boolean previousMatches = MessageDigest.isEqual(previous, candidate);
        if (previous.length >= TaskRecoveryConstant.MIN_MACHINE_KEY_BYTES && rotated != null
                && !rotated.isAfter(now) && now.isBefore(rotated.plusSeconds(TaskRecoveryConstant.A2A_TTL_SECONDS))) {
            accepted |= previousMatches;
        }
        if (!accepted) {
            log.info("Task 恢复机器身份拒绝，reason=RECOVERY_ISSUANCE_FAILED");
            throw new BusinessException(ErrorCode.FORBIDDEN, "RECOVERY_ISSUANCE_FAILED");
        }
    }

    private Jwt requireHistoricalProof(TaskRecoveryIssueDTO request) {
        if (request == null || request.identity() == null || request.sourceCapability() == null
                || request.sourceCapability().length() > TaskRecoveryConstant.MAX_PROOF_LENGTH
                || request.a2aTaskId() == null || request.a2aTaskId().isBlank()
                || request.a2aTaskId().length() > TaskRecoveryConstant.MAX_A2A_ID_LENGTH) { throw denied(); }
        UUID.fromString(request.recoveryId());
        Jwt jwt = historicalDecoder.decode(request.sourceCapability());
        Instant now = Instant.now();
        if (!"RS256".equals(jwt.getHeaders().get("alg"))
                || !jwtService.props().issuer().equals(jwt.getClaimAsString("iss"))
                || !List.of(JwtConstant.TASK_CAPABILITY_AUDIENCE).equals(jwt.getAudience())
                || !JwtConstant.ACTOR_AGENT.equals(jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE))
                || !JwtConstant.SCOPE_AGENT.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SCOPE))
                || jwt.getId() == null || jwt.getId().isBlank()
                || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(now)
                || jwt.getExpiresAt() == null || jwt.getExpiresAt().isAfter(now)
                || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                || (jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now))
                || !String.valueOf(request.identity().taskId()).equals(jwt.getSubject())
                || jwt.getClaimAsStringList(JwtConstant.CLAIM_AGENT_ACTIONS) == null) { throw denied(); }
        request.identity().toClaims().forEach((key, value) -> {
            Object actual = jwt.getClaim(key);
            if (JwtConstant.CLAIM_DERIVATION_REQUEST_HASH.equals(key) && value == null && actual == null) { return; }
            if (value == null || actual == null || !String.valueOf(value).equals(String.valueOf(actual))) {
                throw denied();
            }
        });
        if (!List.of(TaskExecutionMode.LIVE.name(), TaskExecutionMode.ISOLATED.name())
                .contains(request.identity().executionMode())) { throw denied(); }
        return jwt;
    }

    private BusinessException denied() {
        return new BusinessException(ErrorCode.FORBIDDEN, "SOURCE_CAPABILITY_INVALID");
    }
}
