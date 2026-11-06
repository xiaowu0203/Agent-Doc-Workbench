package com.agentdoc.gateway.security;

import com.agentdoc.gateway.config.GatewayAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** P0公共入口拒绝回归（原放行探针），不可被白名单/OPTIONS/路径编码绕过。 */
class P0SigningRouteBaselineTest {

    @Test
    void rejectsSigningAndExecutionProfileBeforeWhitelistAndOptions() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        GatewayAuthProperties properties = new GatewayAuthProperties();
        properties.setWhitelist(List.of("/**"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(decoder, properties);

        for (String path : List.of("/api/auth/internal/task-capabilities",
                "/api/auth/internal/evaluation-worker-capabilities",
                "/api/agent/internal/agents/1/execution-profile",
                "/api/%61uth/internal/task-capabilities",
                "/api/auth;param=1/internal/task-capabilities",
                "/api/auth//internal/evaluation-worker-capabilities",
                "/api/other/../agent/internal/agents/1/execution-profile")) {
            for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.OPTIONS)) {
                MockServerWebExchange exchange = MockServerWebExchange.from(
                        MockServerHttpRequest.method(method, path)
                                .header("Authorization", "Bearer p0-user").build());
                AtomicBoolean forwarded = new AtomicBoolean();
                filter.filter(exchange, next -> {
                    forwarded.set(true);
                    return Mono.empty();
                }).block();
                assertThat(forwarded).as(path).isFalse();
                assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            }
        }
        verifyNoInteractions(decoder);
    }
}
