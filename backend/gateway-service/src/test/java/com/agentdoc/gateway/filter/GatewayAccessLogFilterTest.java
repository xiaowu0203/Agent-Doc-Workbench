package com.agentdoc.gateway.filter;

import com.agentdoc.common.constant.HeaderConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class GatewayAccessLogFilterTest {

    @Test
    void logsRequestStartAndCompletion(CapturedOutput output) {
        GatewayAccessLogFilter filter = new GatewayAccessLogFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header(HeaderConstants.X_TRACE_ID, "trace-123")
                        .build());

        filter.filter(exchange, filteredExchange -> {
            filteredExchange.getResponse().setStatusCode(HttpStatus.OK);
            assertNull(filteredExchange.getRequest().getHeaders().getFirst(HeaderConstants.X_TRACE_ID));
            return Mono.empty();
        }).block();

        assertTrue(output.getOut().contains("收到请求 method=POST path=/api/auth/login traceId="));
        assertTrue(output.getOut().contains(
                "请求完成 method=POST path=/api/auth/login status=200"));
    }

    @Test
    void redactsRequestPathAndFailureStack(CapturedOutput output) {
        GatewayAccessLogFilter filter = new GatewayAccessLogFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test/token=path-test-secret").build());

        assertThrows(IllegalStateException.class, () -> filter.filter(exchange,
                ignored -> Mono.error(new IllegalStateException(
                        "Authorization: Bearer stack-test-secret"))).block());

        assertTrue(output.getOut().contains("[REDACTED]"));
        assertFalse(output.getOut().contains("path-test-secret"));
        assertFalse(output.getOut().contains("stack-test-secret"));
    }
}
