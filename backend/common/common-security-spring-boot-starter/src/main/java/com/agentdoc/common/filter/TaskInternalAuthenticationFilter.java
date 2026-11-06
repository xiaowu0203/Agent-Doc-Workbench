package com.agentdoc.common.filter;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.TaskInternalAuthProperties;
import com.agentdoc.common.constant.TaskInternalAuthConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.utils.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationConverter;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** 指定内部入口的过渡机器认证；不解析人类Token，不裁决领域权限。 */
public class TaskInternalAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> LOOPBACK_ADDRESSES = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    private final TaskInternalAuthProperties properties;
    private final List<String> paths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final BasicAuthenticationConverter converter = new BasicAuthenticationConverter();

    public TaskInternalAuthenticationFilter(TaskInternalAuthProperties properties, List<String> paths) {
        this.properties = properties;
        this.paths = List.copyOf(paths);
        converter.setCredentialsCharset(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 与MVC的解码路径对齐，再规范化额外编码/matrix参数，不能只匹配原始URI。
        String path = StringUtils.cleanPath(UriUtils.decode(
                UrlPathHelper.defaultInstance.getPathWithinApplication(request), StandardCharsets.UTF_8)
                .replaceAll(";[^/]*", "").replaceAll("/{2,}", "/"));
        return paths.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] expected = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (expected.length < TaskInternalAuthConstant.MIN_SECRET_BYTES) {
            reject(response, ErrorCode.SERVICE_UNAVAILABLE);
            return;
        }
        if (!request.isSecure() && !properties.isTrustedEncryptedNetwork()
                && !(LOOPBACK_ADDRESSES.contains(request.getRemoteAddr())
                && LOOPBACK_ADDRESSES.contains(request.getLocalAddr()))) {
            reject(response, ErrorCode.FORBIDDEN);
            return;
        }
        UsernamePasswordAuthenticationToken supplied;
        try {
            supplied = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION)).size() == 1
                    ? converter.convert(request) : null;
        } catch (AuthenticationException exception) {
            reject(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        if (supplied == null || !TaskInternalAuthConstant.CLIENT_ID.equals(supplied.getName())
                || !MessageDigest.isEqual(expected,
                supplied.getCredentials().toString().getBytes(StandardCharsets.UTF_8))) {
            reject(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                TaskInternalAuthConstant.CLIENT_ID, null,
                List.of(new SimpleGrantedAuthority(TaskInternalAuthConstant.AUTHORITY))));
        try {
            SecurityContextHolder.setContext(context);
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void reject(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (code == ErrorCode.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"task-internal\", charset=\"UTF-8\"");
        }
        response.getWriter().write(JsonUtils.toJson(Result.fail(code)));
    }
}
