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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertEquals("trace-123", filteredExchange.getRequest().getHeaders()
                    .getFirst(HeaderConstants.X_TRACE_ID));
            return Mono.empty();
        }).block();

        assertTrue(output.getOut().contains(
                "收到请求 method=POST path=/api/auth/login traceId=trace-123"));
        assertTrue(output.getOut().contains(
                "请求完成 method=POST path=/api/auth/login status=200"));
    }
}
