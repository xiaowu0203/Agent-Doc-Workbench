package com.agentdoc.common.web;

import com.agentdoc.common.context.TraceContext;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * TraceId 链路追踪过滤器：生成/复用上游传递的链路ID，设置到 {@link TraceContext} 与日志MDC，写入HTTP响应头返回。
 * <p>执行顺序最高优先级 {@link Ordered#HIGHEST_PRECEDENCE}，请求最先进入、最后退出，保证全链路日志可带上traceId。</p>
 * <p>逻辑：
 * <ul>
 * <li>优先读取请求头 {@link #TRACE_HEADER} 继承上游链路ID；</li>
 * <li>上游未传递则本地生成无横线UUID作为traceId；</li>
 * <li>存入自定义TraceContext、日志MDC，同时写入响应头返回给调用方；</li>
 * <li>finally块强制清理上下文，避免线程池线程复用导致上下文污染。</li>
 * </ul>
 * </p>
 * <strong>仅Servlet(SpringMVC)环境生效，WebFlux网关环境不使用本过滤器</strong>。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {
    /** HTTP 请求/响应头名称，透传链路TraceId */
    public static final String TRACE_HEADER = "X-Trace-Id";

    /**
     * 过滤器核心处理逻辑。
     * @param request Http请求
     * @param response Http响应
     * @param filterChain 过滤器链
     * @throws ServletException servlet异常
     * @throws IOException IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 优先从请求头继承上游传递的traceId
        String traceId = request.getHeader(TRACE_HEADER);
        // 无上游链路ID，则本地生成，去除UUID横线
        if (StringUtils.isBlank(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        // 设置自定义链路上下文、日志MDC，日志pattern配置 %X{traceId} 即可打印链路ID
        TraceContext.set(traceId);
        MDC.put("traceId", traceId);
        // 响应头回写traceId，方便调用方拿到链路标识排查问题
        response.setHeader(TRACE_HEADER, traceId);
        long startedNanos = System.nanoTime();
        log.info("收到请求 method={} path={} traceId={}", request.getMethod(), request.getRequestURI(), traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedNanos) / 1_000_000L;
            log.info("请求完成 method={} path={} status={} durationMs={} traceId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, traceId);
            // 清理上下文，防止Tomcat线程池复用线程，上下文残留污染下一次请求
            TraceContext.clear();
            MDC.remove("traceId");
        }
    }
}
