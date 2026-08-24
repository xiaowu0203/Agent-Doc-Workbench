package com.agentdoc.common.security;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.context.TaskCapabilityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Agent任务能力令牌过滤器，解析请求头 {@link HeaderConstants#X_TASK_CAPABILITY}。
 * <p>
 * 执行顺序：{@code @Order(Ordered.LOWEST_PRECEDENCE - 10)}，在Spring Security内置过滤器链靠后位置执行。
 * <ol>
 * <li>提取HTTP请求头 X‑TASK‑CAPABILITY；无令牌直接放行，交由原有认证流程处理；</li>
 * <li>调用{@link TaskCapabilityVerifier}完成JWT验签以及Agent业务声明校验；</li>
 * <li>校验通过：将Agent身份写入{@link SecurityContextHolder}，同时写入自定义ThreadLocal{@link TaskCapabilityContext}；</li>
 * <li>TaskCapabilityContext用于Feign拦截器自动向下游透传 X‑TASK‑CAPABILITY 请求头；</li>
 * <li>令牌校验异常：保持原有上下文不变，直接返回HTTP 401；</li>
 * <li>finally清理{@link TaskCapabilityContext}并恢复原始SecurityContext，防止线程池复用发生泄漏。</li>
 * </ol>
 * </p>
 * <strong>重要限制：</strong>
 * <ul>
 * <li>仅处理Servlet同步HTTP请求；@Async / MQ异步线程不会进入该Filter，异步场景需要业务代码手动操作{@link TaskCapabilityContext}，务必try‑finally调用clear()。</li>
 * <li>该Filter不会自动开启，需要配置 {@code agent‑doc.security.task‑capability‑filter‑enabled=true} 才会实例化。</li>
 * </ul>
 */
@Order(TaskCapabilityAuthenticationFilter.FILTER_ORDER)
public class TaskCapabilityAuthenticationFilter extends OncePerRequestFilter {

    public static final int FILTER_ORDER = Ordered.LOWEST_PRECEDENCE - 10;

    /** Agent任务令牌验签器，负责JWT签名、过期、agent业务claim校验 */
    private final TaskCapabilityVerifier verifier;

    public TaskCapabilityAuthenticationFilter(TaskCapabilityVerifier verifier) {
        this.verifier = verifier;
    }

    /**
     * 过滤器核心处理逻辑，每个请求仅执行一次
     * @param request http请求
     * @param response http响应
     * @param filterChain 过滤器链
     * @throws ServletException servlet异常
     * @throws IOException IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 获取Agent任务短时能力令牌
        String token = request.getHeader(HeaderConstants.X_TASK_CAPABILITY);
        // 请求头不存在令牌，直接放行，走原有认证链路
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        Jwt jwt;
        try {
            jwt = verifier.verify(token);
        } catch (RuntimeException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        SecurityContext originalContext = SecurityContextHolder.getContext();
        SecurityContext capabilityContext = SecurityContextHolder.createEmptyContext();
        capabilityContext.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        try {
            // 验签成功，将Agent身份写入SpringSecurity上下文；权限集合暂时传空列表，细粒度鉴权交给业务层requireAgentCapability
            SecurityContextHolder.setContext(capabilityContext);
            // 设置自定义线程上下文，供Feign拦截器读取并向下游透传令牌头
            TaskCapabilityContext.set(token);
            // 继续向后执行过滤器链：Interceptor → Controller → Service
            filterChain.doFilter(request, response);
        } finally {
            // 自定义ThreadLocal，框架不会自动回收，必须强制清除，避免tomcat线程池上下文污染
            TaskCapabilityContext.clear();
            SecurityContextHolder.setContext(originalContext);
        }
    }
}
