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

/**
 * 指定内部入口的过渡机器认证；不解析人类Token，不裁决领域权限。
 */
public class TaskInternalAuthenticationFilter extends OncePerRequestFilter {

    // 回环地址白名单
    private static final Set<String> LOOPBACK_ADDRESSES = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    // 内部认证配置（接收方secret、可信内网开关）
    private final TaskInternalAuthProperties properties;
    // 需要拦截校验的路径模式列表
    private final List<String> paths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    // Basic Auth解析转换器，指定UTF-8编码
    private final BasicAuthenticationConverter converter = new BasicAuthenticationConverter();

    public TaskInternalAuthenticationFilter(TaskInternalAuthProperties properties, List<String> paths) {
        this.properties = properties;
        this.paths = List.copyOf(paths);
        converter.setCredentialsCharset(StandardCharsets.UTF_8);
    }

    /**
     * 路径匹配判断：不匹配配置路径则跳过该过滤器
     * 对齐MVC解码逻辑，清洗matrix参数、连续斜杠，避免编码差异导致匹配失效
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = StringUtils.cleanPath(UriUtils.decode(
                        UrlPathHelper.defaultInstance.getPathWithinApplication(request), StandardCharsets.UTF_8)
                .replaceAll(";[^/]*", "").replaceAll("/{2,}", "/"));
        return paths.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] expected = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        // secret长度不足，直接拒绝服务
        if (expected.length < TaskInternalAuthConstant.MIN_SECRET_BYTES) {
            reject(response, ErrorCode.SERVICE_UNAVAILABLE);
            return;
        }
        // 安全网络校验：非HTTPS、非可信内网、非本机回环访问 → 禁止访问
        if (!request.isSecure() && !properties.isTrustedEncryptedNetwork()
                && !(LOOPBACK_ADDRESSES.contains(request.getRemoteAddr())
                && LOOPBACK_ADDRESSES.contains(request.getLocalAddr()))) {
            reject(response, ErrorCode.FORBIDDEN);
            return;
        }

        UsernamePasswordAuthenticationToken supplied;
        try {
            // 只允许一条Authorization头，多头部直接判定无效
            supplied = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION)).size() == 1
                    ? converter.convert(request) : null;
        } catch (AuthenticationException exception) {
            reject(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        // 校验clientId与secret；使用MessageDigest.isEqual防时序攻击
        if (supplied == null || !TaskInternalAuthConstant.CLIENT_ID.equals(supplied.getName())
                || !MessageDigest.isEqual(expected,
                supplied.getCredentials().toString().getBytes(StandardCharsets.UTF_8))) {
            reject(response, ErrorCode.UNAUTHORIZED);
            return;
        }

        // 保存原有SecurityContext，校验通过后写入机器身份，请求结束恢复上下文，防止ThreadLocal泄露
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

    /**
     * 统一拒绝响应：返回JSON错误体，添加缓存控制头；401场景追加Basic质询头
     */
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
