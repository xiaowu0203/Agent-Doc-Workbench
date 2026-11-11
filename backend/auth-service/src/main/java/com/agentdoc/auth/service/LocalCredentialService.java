package com.agentdoc.auth.service;

import com.agentdoc.auth.constant.PlatformManagementConstant;
import com.agentdoc.auth.enums.AuthenticationAuditEvent;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.utils.AuthenticationPrivacyUtils;
import com.agentdoc.common.constant.IdentityTokenConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.LocalHumanCredentialsDTO;
import com.agentdoc.common.feign.dto.ServiceProofRequestDTO;
import com.agentdoc.common.feign.vo.LocalHumanCredentialVO;
import com.agentdoc.common.feign.vo.ServiceProofVO;
import com.agentdoc.common.feign.vo.UserRefVO;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.net.InetAddressUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;

/** 密码/限流用例和受限签发；服务签发不触发用户解析或远程调用。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalCredentialService {
    private final LocalClientRegistry clients;
    private final LocalLoginFailureLimiter limiter;
    private final AuthService authentication;
    private final LocalIdentityJwtService signer;
    private final PlatformRoleService roles;
    private final PlatformAuditLogService audit;

    @PreAuthorize("@localClientRegistry.isCurrentBff()")
    public LocalHumanCredentialVO issueHuman(LocalHumanCredentialsDTO request) {
        if (request == null || request.username() == null || request.clientIp() == null) { throw new BusinessException(ErrorCode.BAD_REQUEST); }
        String username = AuthenticationPrivacyUtils.normalizeUsername(request.username());
        if (username.length() < PlatformManagementConstant.MIN_USERNAME_LENGTH
                || username.length() > PlatformManagementConstant.MAX_USERNAME_LENGTH
                || !username.matches(PlatformManagementConstant.USERNAME_PATTERN)) { throw new BusinessException(ErrorCode.BAD_REQUEST); }
        String ip = normalizedIp(request.clientIp());
        Map<String, String> detail = Map.of("client", clients.currentClient(), "accountHash", AuthenticationPrivacyUtils.digest(username),
                "ipHash", AuthenticationPrivacyUtils.digest(ip));
        LocalLoginFailureLimiter.Reservation reservation;
        try { reservation = limiter.reserve(username, ip); }
        catch (BusinessException error) {
            audit.recordAuthentication((error.getCode() == ErrorCode.TOO_MANY_REQUESTS.getCode()
                    ? AuthenticationAuditEvent.LOCAL_LOGIN_THROTTLED : AuthenticationAuditEvent.LOCAL_LOGIN_UNAVAILABLE).name(), null, detail);
            throw error;
        }
        UserEntity user;
        try { user = authentication.authenticateLocalUser(username, request.password()); }
        catch (DataAccessException error) {
            limiter.release(reservation);
            audit.recordAuthentication(AuthenticationAuditEvent.LOCAL_LOGIN_UNAVAILABLE.name(), null, detail);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        } catch (BusinessException error) {
            if (error.getCode() >= ErrorCode.INTERNAL_ERROR.getCode()) { limiter.release(reservation); }
            audit.recordAuthentication((error.getCode() >= ErrorCode.INTERNAL_ERROR.getCode()
                    ? AuthenticationAuditEvent.LOCAL_LOGIN_UNAVAILABLE : AuthenticationAuditEvent.LOCAL_LOGIN_FAILED).name(), null, detail);
            throw error;
        }
        try { limiter.release(reservation); }
        catch (BusinessException error) {
            audit.recordAuthentication(AuthenticationAuditEvent.LOCAL_LOGIN_UNAVAILABLE.name(), null, detail);
            throw error;
        }
        var credential = signer.issueHuman(user);
        var currentRoles = roles.listRoleKeys(user.getId());
        audit.recordAuthentication(AuthenticationAuditEvent.LOCAL_LOGIN_SUCCEEDED.name(), user.getId(),
                Map.of("client", clients.currentClient(), "sessionId", credential.sessionId(), "expiresAt", credential.expiresAt().toString()));
        return new LocalHumanCredentialVO(credential.value(), new UserRefVO(user.getId(), user.getUsername(), user.getNickname(), true),
                currentRoles, user.getAuthVersion(), credential.sessionId(), credential.issuedAt(), credential.expiresAt());
    }

    @PreAuthorize("@localClientRegistry.isCurrentService()")
    public ServiceProofVO issueService(ServiceProofRequestDTO request) {
        if (request == null || !IdentityTokenConstant.SERVICE_AUDIENCE.equals(request.audience())
                || !Set.of(IdentityTokenConstant.SERVICE_SCOPE).equals(request.scopes())) { throw new BusinessException(ErrorCode.BAD_REQUEST); }
        String client = clients.currentClient();
        var credential = signer.issueService(client);
        audit.recordAuthentication(AuthenticationAuditEvent.LOCAL_SERVICE_PROOF_ISSUED.name(), null,
                Map.of("client", client, "expiresAt", credential.expiresAt().toString()));
        return new ServiceProofVO(credential.value(), credential.issuedAt(), credential.expiresAt());
    }

    private static String normalizedIp(String value) {
        if (value.length() > 45 || value.contains("%") || !(InetAddressUtils.isIPv4Address(value) || InetAddressUtils.isIPv6Address(value))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "客户端IP必须是受信字面量");
        }
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (UnknownHostException error) { throw new BusinessException(ErrorCode.BAD_REQUEST, "客户端IP无效"); }
    }
}
