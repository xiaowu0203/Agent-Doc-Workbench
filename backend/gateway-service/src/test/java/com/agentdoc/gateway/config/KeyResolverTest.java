package com.agentdoc.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyResolverTest {

    @Test
    void resolvesByClientIp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
                        .remoteAddress(new InetSocketAddress("192.168.1.10", 8080))
                        .build());

        KeyResolver resolver = new GatewaySecurityConfig().clientIpKeyResolver();
        assertEquals("192.168.1.10", resolver.resolve(exchange).block());
    }
}
