package com.agentdoc.common.feign.interceptor;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.context.TraceContext;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.feign.context.AuthorizationContext;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class AuthHeaderForwardInterceptorTest {

    @AfterEach
    void clearContext() {
        TraceContext.clear();
        TaskCapabilityContext.clear();
        AuthorizationContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void doesNotUseCompatibilityTraceHeaderForPropagation() {
        TraceContext.set("legacy-trace-id");
        RequestTemplate template = new RequestTemplate();

        new AuthHeaderForwardInterceptor().apply(template);

        assertThat(template.headers()).doesNotContainKey(HeaderConstants.X_TRACE_ID);
    }

    @Test
    void dedicatedRecoveryHeadersExcludeBothOrdinaryIdentityContextsRegardlessOfHeaderCase() {
        AuthorizationContext.set("Bearer user-token");
        TaskCapabilityContext.set("expired-proof");
        for (String header : new String[]{TaskRecoveryConstant.MACHINE_KEY_HEADER, TaskRecoveryConstant.CAPABILITY_HEADER.toLowerCase(),
                OnlineCapabilityConstant.HEADER.toLowerCase()}) {
            RequestTemplate template = new RequestTemplate();
            template.header(header, "dedicated-credential");
            template.header("Authorization", "Bearer existing");
            template.header(HeaderConstants.X_TASK_CAPABILITY, "old");
            new AuthHeaderForwardInterceptor().apply(template);
            assertThat(template.headers()).doesNotContainKeys("Authorization", HeaderConstants.X_TASK_CAPABILITY);
            assertThat(template.headers().values()).anySatisfy(values -> assertThat(values).contains("dedicated-credential"));
        }
    }

    @Test
    void explicitNarrowAuthorizationIsNotCombinedWithTheCurrentUser() {
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AuthorizationContext.set("Bearer background-token");
        var template = new RequestTemplate(); template.header("Authorization", "Bearer explicit-narrow-token");
        new AuthHeaderForwardInterceptor().apply(template);
        assertThat(template.headers().get("Authorization")).containsExactly("Bearer explicit-narrow-token");
    }
}
