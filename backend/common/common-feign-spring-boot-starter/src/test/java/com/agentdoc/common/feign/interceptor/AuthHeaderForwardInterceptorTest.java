package com.agentdoc.common.feign.interceptor;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.context.TraceContext;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthHeaderForwardInterceptorTest {

    @AfterEach
    void clearContext() {
        TraceContext.clear();
    }

    @Test
    void doesNotUseCompatibilityTraceHeaderForPropagation() {
        TraceContext.set("legacy-trace-id");
        RequestTemplate template = new RequestTemplate();

        new AuthHeaderForwardInterceptor().apply(template);

        assertThat(template.headers()).doesNotContainKey(HeaderConstants.X_TRACE_ID);
    }
}
