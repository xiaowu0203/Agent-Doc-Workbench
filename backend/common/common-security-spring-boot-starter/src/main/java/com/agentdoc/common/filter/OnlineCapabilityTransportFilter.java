package com.agentdoc.common.filter;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 专用线上凭证只走加密内部入口，并拒绝混合用户/Task 身份。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OnlineCapabilityTransportFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean internal;
        try {
            String path = StringUtils.cleanPath(UriUtils.decode(request.getRequestURI(), StandardCharsets.UTF_8)
                    .replaceAll(";[^/]*", "").replaceAll("/{2,}", "/"));
            internal = OnlineCapabilityUtils.internalPath(path);
        } catch (IllegalArgumentException invalid) { deny(response); return; }
        boolean narrow = request.getHeader(OnlineCapabilityConstant.HEADER) != null;
        if (!internal && !narrow) { chain.doFilter(request, response); return; }
        boolean encrypted = request.isSecure()
                || Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr());
        if (!internal || !encrypted || narrow && (request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || request.getHeader(HeaderConstants.X_TASK_CAPABILITY) != null)) { deny(response); return; }
        chain.doFilter(request, response);
    }

    private void deny(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JsonUtils.toJson(Result.fail(ErrorCode.FORBIDDEN)));
    }
}
