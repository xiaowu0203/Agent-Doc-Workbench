package com.agentdoc.common.filter;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.utils.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 只保护恢复入口的传输；loopback 例外不是机器身份认证，后续仍须验证专属密钥/JWT。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TaskRecoveryTransportFilter extends OncePerRequestFilter {
    private final SecurityVerifyProperties properties;

    public TaskRecoveryTransportFilter(SecurityVerifyProperties properties) { this.properties = properties; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path;
        try { path = StringUtils.cleanPath(UriUtils.decode(request.getRequestURI(), StandardCharsets.UTF_8)
                .replaceAll(";[^/]*", "").replaceAll("/{2,}", "/")); }
        catch (IllegalArgumentException exception) { return false; }
        return !(path.startsWith("/api/auth/internal/task-recovery-capabilities")
                || path.startsWith("/api/auth/internal/task-draft-finalization-capabilities")
                || path.startsWith("/api/agent/internal/a2a/")
                || path.startsWith("/api/document/internal/task-drafts/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 不信任 X-Forwarded-For/Proto：只有容器确认的 TLS、实际 loopback 或运维显式信任可通过。
        if (request.isSecure() || properties.isTaskRecoveryTrustedEncryptedNetwork()
                || Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JsonUtils.toJson(Result.fail(ErrorCode.FORBIDDEN)));
    }
}
