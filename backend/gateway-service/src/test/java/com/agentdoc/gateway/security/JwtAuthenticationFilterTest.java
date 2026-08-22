package com.agentdoc.gateway.security;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.gateway.config.GatewayAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtDecoder jwtDecoder;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        filter = new JwtAuthenticationFilter(jwtDecoder, new GatewayAuthProperties());
    }

    private MockServerWebExchange exchange(String path, String... header) {
        if (header.length == 2) {
            return MockServerWebExchange.from(
                    MockServerHttpRequest.get(path).header(header[0], header[1]).build());
        }
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    /** 白名单路径放行，且剥离入站伪造的 X-User-* 头 */
    @Test
    void whitelistPassesThroughAndStripsSpoofedHeaders() {
        MockServerWebExchange exchange =
                exchange("/api/auth/login", HeaderConstants.X_USER_ID, "999");

        ServerWebExchange[] captured = new ServerWebExchange[1];
        GatewayFilterChain chain = ex -> {
            captured[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNull(captured[0].getResponse().getStatusCode(), "白名单不应返回 401");
        assertNull(captured[0].getRequest().getHeaders().getFirst(HeaderConstants.X_USER_ID),
                "入站伪造的 X-User-Id 必须被剥离");
        assertTrue(captured[0].getRequest().getHeaders().containsKey(HeaderConstants.X_TRACE_ID),
                "应注入 X-Trace-Id");
    }

    /** 无 Authorization 头 → 401 + Result JSON */
    @Test
    void missingTokenReturns401Json() {
        MockServerWebExchange exchange = exchange("/api/auth/me");
        GatewayFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body.contains("\"code\":40100"), "响应体应包含统一错误码, actual=" + body);
    }

    /** 有效 token → 注入 X-User-* 头、保留 Authorization、放行下游 */
    @Test
    void validTokenInjectsIdentityAndRetainsAuthorization() {
        Jwt jwt = Jwt.withTokenValue("signed.jwt.value")
                .header("alg", "RS256")
                .claim("sub", "10")
                .claim("username", "alice")
                .claim("nickname", "Alice")
                .claim("scope", "read,write")
                .build();
        when(jwtDecoder.decode("signed.jwt.value")).thenReturn(jwt);

        MockServerWebExchange exchange =
                exchange("/api/auth/me", "Authorization", "Bearer signed.jwt.value");

        ServerWebExchange[] captured = new ServerWebExchange[1];
        GatewayFilterChain chain = ex -> {
            captured[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNull(captured[0].getResponse().getStatusCode(), "有效 token 不应返回 401");
        ServerHttpRequest downstream = captured[0].getRequest();
        assertEquals("10", downstream.getHeaders().getFirst(HeaderConstants.X_USER_ID));
        assertEquals("alice", downstream.getHeaders().getFirst(HeaderConstants.X_USER_NAME));
        assertEquals("Alice", downstream.getHeaders().getFirst(HeaderConstants.X_USER_NICKNAME));
        assertEquals("read,write", downstream.getHeaders().getFirst(HeaderConstants.X_USER_SCOPES));
        assertEquals("Bearer signed.jwt.value",
                downstream.getHeaders().getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION),
                "应保留原 Authorization 头供下游二次校验");
    }

    /** 非法 token（解码失败）→ 401 + Result JSON */
    @Test
    void invalidTokenReturns401Json() {
        when(jwtDecoder.decode("a.b.c")).thenThrow(new JwtException("invalid signature"));

        MockServerWebExchange exchange =
                exchange("/api/auth/me", "Authorization", "Bearer a.b.c");
        GatewayFilterChain chain = ex -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body.contains("\"code\":40100"), "响应体应包含统一错误码, actual=" + body);
    }
}
