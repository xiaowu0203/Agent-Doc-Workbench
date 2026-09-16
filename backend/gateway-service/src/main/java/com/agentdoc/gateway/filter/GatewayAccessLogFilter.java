package com.agentdoc.gateway.filter;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.context.TraceContext;
import com.agentdoc.common.logging.LogSanitizer;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gateway全局访问日志过滤器
 * 功能：
 * 1. 读取 OTel Trace ID，作为 X-Trace-Id 响应头与日志兼容值；下游传播由 W3C traceparent 负责
 * 2. 记录网关入口请求日志：请求方法、路径、traceId
 * 3. 请求异常时打印错误日志，携带堆栈信息
 * 4. 请求结束后打印完成日志，记录响应状态码、耗时、traceId
 * 5. 高优先级执行，保证traceId在网关最早期就注入
 */
@Slf4j
@Component
public class GatewayAccessLogFilter implements GlobalFilter, Ordered {

    /**
     * 过滤器执行顺序，值越小优先级越高
     * -200 设置较高优先级，保证在网关业务处理之前执行，尽早注入traceId
     */
    private static final int FILTER_ORDER = -200;

    /**
     * 网关全局过滤核心逻辑
     * @param exchange 请求上下文对象，封装request、response、属性等web上下文
     * @param chain 网关过滤器链，执行chain.filter继续向后流转
     * @return Mono<Void> 响应异步流
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Java Agent 已在 WebFlux Filter 前建立服务端 Span；禁用 OTel 时使用本地兼容 ID。
        String telemetryTraceId = TraceContext.getTelemetryTraceId();
        String traceId = StringUtils.isBlank(telemetryTraceId)
                ? UUID.randomUUID().toString().replace("-", "")
                : telemetryTraceId;

        // X-Trace-Id 只作为响应/日志兼容字段，不再向下游传播。
        ServerHttpRequest enrichedRequest = request.mutate()
                .headers(headers -> headers.remove(HeaderConstants.X_TRACE_ID))
                .build();

        // 使用新request构建新的exchange上下文对象
        ServerWebExchange enrichedExchange = exchange.mutate().request(enrichedRequest).build();

        // 将traceId写入响应头，返回给客户端，方便问题排查
        enrichedExchange.getResponse().getHeaders().set(HeaderConstants.X_TRACE_ID, traceId);

        // 获取请求方法、请求路径，记录入参日志
        String method = request.getMethod().name();
        // 防止路径中包含敏感信息，如用户信息、密码等
        String path = LogSanitizer.sanitizeText(request.getURI().getPath());

        // 记录请求开始时间，使用纳秒保证耗时计算精度
        long startedNanos = System.nanoTime();

        // 打印网关收到请求日志
        log.info("收到请求 method={} path={} traceId={}", method, path, traceId);

        return chain.filter(enrichedExchange)
                // 捕获链路中的异常，打印错误日志，携带异常堆栈
                .doOnError(error -> log.error(
                        "请求异常 method={} path={} durationMs={} traceId={} stack={}",
                        method, path, elapsedMillis(startedNanos), traceId,
                        LogSanitizer.sanitizeThrowable(error)))
                // doFinally：无论正常结束、异常、取消，都会执行，打印请求完成日志统计耗时与状态码
                .doFinally(signalType -> {
                    HttpStatusCode status = enrichedExchange.getResponse().getStatusCode();
                    // 状态码为空兜底200，避免NPE
                    int statusCode = status == null ? 200 : status.value();
                    log.info("请求完成 method={} path={} status={} durationMs={} traceId={}",
                            method, path, statusCode, elapsedMillis(startedNanos), traceId);
                });
    }

    /**
     * 计算接口耗时，纳秒转毫秒
     * @param startedNanos 请求开始时刻纳秒时间戳
     * @return 执行耗时(ms)
     */
    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    /**
     * 返回过滤器优先级
     * @return 执行order值
     */
    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}
