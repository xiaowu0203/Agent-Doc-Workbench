package com.agentdoc.auth.security;

import com.agentdoc.auth.config.LocalIdentityProperties;
import com.agentdoc.auth.constant.LocalIdentityConstant;
import com.agentdoc.auth.service.LocalClientRegistry;
import com.agentdoc.auth.service.PlatformAuditLogService;
import com.agentdoc.auth.enums.AuthenticationAuditEvent;
import com.agentdoc.auth.enums.LocalClientPurpose;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.Map;
import org.springframework.dao.DataAccessException;

/** 仅保护两个受控签发入口；认证本身不查用户、不调用本人Feign。 */
@RequiredArgsConstructor
public class LocalClientAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    private static final String BASIC_PREFIX = "Basic ";
    private final LocalClientRegistry clients;
    private final LocalIdentityProperties properties;
    private final PlatformAuditLogService audit;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !LocalIdentityConstant.HUMAN_CREDENTIAL_PATH.equals(path) && !LocalIdentityConstant.SERVICE_PROOF_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (!request.isSecure() && !(LOOPBACK.contains(request.getRemoteAddr()) && LOOPBACK.contains(request.getLocalAddr()))
                && !properties.isTrustedEncryptedNetwork()) {
            rejectedClient(response, ErrorCode.FORBIDDEN, Map.of("outcome", "insecure_transport")); return;
        }
        LocalClientAuthentication authentication;
        try {
            var headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
            if (headers.size() != 1) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
            String header = headers.getFirst();
            if (header.length() > LocalIdentityConstant.MAX_CLIENT_AUTHORIZATION_LENGTH
                    || !header.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            String decoded = new String(Base64.getDecoder().decode(header.substring(BASIC_PREFIX.length())), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator <= 0) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
            authentication = clients.authenticate(decoded.substring(0, separator), decoded.substring(separator + 1));
        } catch (IllegalArgumentException | BusinessException error) {
            rejectedClient(response, ErrorCode.UNAUTHORIZED, Map.of("outcome", "invalid_client")); return;
        }
        boolean human = request.getRequestURI().endsWith(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH);
        if (!authentication.hasPurpose(human ? LocalClientPurpose.BFF : LocalClientPurpose.SERVICE)) {
            rejectedClient(response, ErrorCode.FORBIDDEN, Map.of("client", authentication.getName(), "outcome", "wrong_purpose")); return;
        }
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try { chain.doFilter(request, response); } finally { SecurityContextHolder.clearContext(); }
    }

    private void rejectedClient(HttpServletResponse response, ErrorCode code, Map<String, String> detail) throws IOException {
        try { audit.recordAuthentication(AuthenticationAuditEvent.LOCAL_CLIENT_REJECTED.name(), null, detail); }
        catch (DataAccessException unavailable) { reject(response, ErrorCode.SERVICE_UNAVAILABLE); return; }
        reject(response, code);
    }

    private static void reject(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getHttpStatus().value()); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name()); response.getWriter().write(JsonUtils.toJson(Result.fail(code)));
    }
}
