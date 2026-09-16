package com.agentdoc.common.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(OutputCaptureExtension.class)
class TraceIdFilterTest {

    @Test
    void logsRequestStartAndCompletion(CapturedOutput output) throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader(TraceIdFilter.TRACE_HEADER, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> response.setStatus(200));

        assertNotEquals("trace-123", response.getHeader(TraceIdFilter.TRACE_HEADER));
        assertTrue(output.getOut().contains("收到请求 method=POST path=/api/auth/login traceId="));
        assertTrue(output.getOut().contains(
                "请求完成 method=POST path=/api/auth/login status=200"));
    }
}
