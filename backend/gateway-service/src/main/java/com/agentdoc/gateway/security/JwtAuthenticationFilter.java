package com.agentdoc.gateway.security;

import com.agentdoc.common.api.ErrorCode;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.context.LoginUser;
import com.agentdoc.common.security.JwtTokenParser;
import com.agentdoc.gateway.config.GatewayAuthProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 网关 JWT 统一鉴权过滤器：
 * - 白名单 / OPTIONS 直接放行（但始终剥离入站 X-User-* 头，防止伪造身份）
 * - 其余请求校验 Bearer Token，通过后注入 X-User-* 头下发下游，保留原 Authorization 供业务二次校验
 * - 校验失败统一返回 401 + Result JSON
 * 公钥通过 auth-service 的 /oauth2/jwks 拉取并缓存（RemoteJWKSet）。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /** 需剥离的伪造风险请求头：由网关独占写入，业务服务不得信任客户端直传 */
    private static final List<String> FORBIDDEN_HEADERS = List.of(
            HeaderConstants.X_USER_ID,
            HeaderConstants.X_USER_NAME,
            HeaderConstants.X_USER_NICKNAME,
            HeaderConstants.X_AGENT_ID,
            HeaderConstants.X_USER_SCOPES,
            HeaderConstants.X_TRACE_ID
    );

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final GatewayAuthProperties properties;
    private final JwtTokenParser jwtTokenParser = new JwtTokenParser();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder, GatewayAuthProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String inboundTraceId = request.getHeaders().getFirst(HeaderConstants.X_TRACE_ID);
        String traceId = (inboundTraceId == null || inboundTraceId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : inboundTraceId;

        // 1. 剥离入站 X-* 头，并注入统一 X-Trace-Id
        ServerHttpRequest stripped = request.mutate().headers(headers -> {
            FORBIDDEN_HEADERS.forEach(headers::remove);
            headers.set(HeaderConstants.X_TRACE_ID, traceId);
        }).build();
        ServerWebExchange cleaned = exchange.mutate().request(stripped).build();

        // 2. OPTIONS 预检与白名单直接放行
        if (request.getMethod() == HttpMethod.OPTIONS || isWhitelisted(path)) {
            return chain.filter(cleaned);
        }

        // 3. 校验 Bearer Token
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(cleaned.getResponse(), "缺少或非法的 Authorization 头");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return unauthorized(cleaned.getResponse(), "缺少或非法的 Authorization 头");
        }

        final Jwt jwt;
        try {
            jwt = jwtTokenParser.decode(token, jwtDecoder);
        } catch (JwtException e) {
            log.debug("JWT 校验失败, path={}, reason={}", path, e.getMessage());
            return unauthorized(cleaned.getResponse(), "无效的访问令牌");
        }

        // 4. 注入用户身份头，保留原 Authorization 供下游二次校验
        LoginUser user = jwtTokenParser.toLoginUser(jwt);
        ServerHttpRequest enriched = stripped.mutate().headers(headers -> {
            headers.set(HeaderConstants.X_USER_ID, String.valueOf(user.userId()));
            headers.set(HeaderConstants.X_USER_NAME, user.username());
            headers.set(HeaderConstants.X_USER_NICKNAME, user.nickname());
            if (user.isAgent()) {
                headers.set(HeaderConstants.X_AGENT_ID, String.valueOf(user.agentId()));
            }
            if (user.scopes() != null && !user.scopes().isEmpty()) {
                headers.set(HeaderConstants.X_USER_SCOPES, String.join(",", user.scopes()));
            }
        }).build();
        return chain.filter(exchange.mutate().request(enriched).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhitelisted(String path) {
        return properties.getWhitelist().stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        final byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Result.fail(ErrorCode.UNAUTHORIZED, message));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
