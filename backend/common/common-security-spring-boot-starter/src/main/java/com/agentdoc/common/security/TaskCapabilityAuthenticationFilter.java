package com.agentdoc.common.security;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.context.TaskCapabilityContext;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Agent任务短时能力令牌认证过滤器
 * <p>
 * 处理两种令牌来源：
 * 1. 请求头 {@link HeaderConstants#X_TASK_CAPABILITY} 携带任务能力令牌，全局接口生效
 * 2. 配置的能力端点从 Authorization: Bearer 头解析JWT令牌
 * <p>
 * 职责：JWT验签、构造Agent安全认证上下文、设置自定义任务能力线程上下文；
 * 验签失败直接返回401；无令牌直接放行走原有Spring Security认证链路；
 * finally块做上下文恢复，防止Tomcat线程池ThreadLocal上下文泄漏与污染。
 * 过滤器顺序：最低优先级-10，晚于普通登录认证，优先于业务鉴权Filter
 */
@Order(TaskCapabilityAuthenticationFilter.FILTER_ORDER)
public class TaskCapabilityAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 过滤器执行顺序
     * {@link Ordered#LOWEST_PRECEDENCE}：Spring Security默认过滤器链末尾，-10略微提前
     */
    public static final int FILTER_ORDER = Ordered.LOWEST_PRECEDENCE - 10;

    /** Bearer token前缀，和JWT常量保持一致 */
    private static final String BEARER_PREFIX = JwtConstant.TOKEN_TYPE_BEARER + " ";
    /** Ant 风格路径匹配器，支持 /mcp/**、/a2a/** 等配置 */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** Agent任务令牌验签器，负责JWT签名、过期、agent业务claim校验 */
    private final TaskCapabilityVerifier verifier;
    /** 允许从Authorization头解析能力令牌的路径模式 */
    private final List<String> capabilityAuthEndpoints;

    /**
     * Agent任务令牌验签器
     * 负责JWT签名校验、过期时间校验、Agent业务自定义Claim合法性校验
     */
    public TaskCapabilityAuthenticationFilter(TaskCapabilityVerifier verifier,
                                              SecurityVerifyProperties properties) {
        this.verifier = verifier;
        this.capabilityAuthEndpoints = properties.getCapabilityAuthEndpoints() == null
                ? List.of()
                : List.copyOf(properties.getCapabilityAuthEndpoints());
    }

    /**
     * 过滤器核心处理逻辑，OncePerRequestFilter保证一次请求只会执行一次过滤
     * @param request        http请求对象
     * @param response       http响应对象
     * @param filterChain    过滤器链，调用doFilter继续执行后续过滤器、拦截器、Controller
     * @throws ServletException servlet容器异常
     * @throws IOException      IO读写异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 解析Agent任务短时能力令牌，支持请求头X‑Task‑Capability 或 MCP/A2A接口Authorization Bearer
        String token = resolveCapability(request);

        // 令牌为空：无Agent任务令牌，直接放行，交给Spring Security原有认证逻辑处理
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt;
        try {
            // 执行验签：签名、过期时间、Agent业务claim全部校验
            jwt = verifier.verify(token);
        } catch (RuntimeException ex) {
            // 验签任意异常（签名非法、过期、业务claim不合法）直接返回401未授权，中断链路
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 保存进入过滤器之前原始Security上下文，请求结束后恢复，避免覆盖原有登录用户身份
        SecurityContext originalContext = SecurityContextHolder.getContext();
        // 创建空安全上下文，写入Agent任务令牌对应的认证对象
        SecurityContext capabilityContext = SecurityContextHolder.createEmptyContext();
        // 构建Agent JWT认证Token；权限集合传空集合，细粒度能力鉴权交给业务层requireAgentCapability做校验
        capabilityContext.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        try {
            // 将Agent身份设置到Spring Security全局上下文，后续Security上下文可拿到该Agent认证信息
            SecurityContextHolder.setContext(capabilityContext);
            // 设置自定义ThreadLocal上下文，供Feign拦截器读取，向下游微服务透传X‑Task‑Capability请求头
            TaskCapabilityContext.set(token);
            // 继续过滤器链流转：后续Interceptor → Controller → Service
            filterChain.doFilter(request, response);
        } finally {
            /*
             * 【极其重要】资源清理
             * TaskCapabilityContext为自定义ThreadLocal，Spring Security不会自动清理；
             * Tomcat线程池复用线程，不清理会造成上下文泄漏、跨请求令牌污染问题。
             */
            TaskCapabilityContext.clear();
            // 恢复进入过滤器之前原始Security上下文，还原原有登录用户身份
            SecurityContextHolder.setContext(originalContext);
        }
    }

    /**
     * 解析Agent任务能力令牌
     * <ol>
     *  <li>优先读取请求头 {@link HeaderConstants#X_TASK_CAPABILITY}</li>
     *  <li>如果请求路径命中配置的能力端点，降级从Authorization Bearer头提取JWT令牌</li>
     * </ol>
     *
     * @param request http请求
     * @return 任务令牌；无令牌返回null
     */
    private String resolveCapability(HttpServletRequest request) {
        // 第一优先级：自定义X‑Task‑Capability请求头
        String capability = request.getHeader(HeaderConstants.X_TASK_CAPABILITY);
        if (StringUtils.isNotBlank(capability)) {
            return capability;
        }

        // 获取请求URI
        String requestUri = request.getRequestURI();
        // 非配置能力端点，不从Authorization头解析任务令牌，直接返回null
        if (!isCapabilityEndpoint(requestUri)) {
            return null;
        }

        // 配置的能力端点，解析Authorization: Bearer xxx头部
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        // 截掉Bearer前缀，返回原始jwt字符串，trim剔除首尾空白字符
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }

    /**
     * 判断当前URI是否命中配置的能力令牌端点
     * 支持Ant风格路径匹配，例如 /mcp/**、/a2a/**。
     *
     * @param requestUri 请求URI
     * @return true=是MCP/A2A接口；false=普通业务接口
     */
    private boolean isCapabilityEndpoint(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        return capabilityAuthEndpoints.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, requestUri));
    }
}
