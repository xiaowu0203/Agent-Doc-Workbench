package com.agentdoc.common.security;

import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.filter.TaskRecoveryTransportFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRecoveryTransportFilterTest {
    @Test
    void refusesNonLoopbackPlainHttpEvenWithForgedForwardedHeaders() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        request.addHeader("X-Forwarded-Proto", "https");
        assertThat(allowed(request, new SecurityVerifyProperties())).isFalse();
        request.setRequestURI("/api/%61uth/internal/task-recovery-capabilities");
        assertThat(allowed(request, new SecurityVerifyProperties())).isFalse();
    }

    @Test
    void allowsTlsLocalDevelopmentOrExplicitlyEncryptedNetwork() throws Exception {
        MockHttpServletRequest request = request();
        request.setSecure(true);
        assertThat(allowed(request, new SecurityVerifyProperties())).isTrue();
        request.setSecure(false);
        request.setRemoteAddr("127.0.0.1");
        assertThat(allowed(request, new SecurityVerifyProperties())).isTrue();
        request.setRemoteAddr("192.0.2.10");
        SecurityVerifyProperties properties = new SecurityVerifyProperties();
        properties.setTaskRecoveryTrustedEncryptedNetwork(true);
        assertThat(allowed(request, properties)).isTrue();
    }

    @Test
    void doesNotChangeUnrelatedBusinessEndpoints() throws Exception {
        MockHttpServletRequest request = request();
        request.setRequestURI("/api/task/tasks/1");
        assertThat(allowed(request, new SecurityVerifyProperties())).isTrue();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/internal/task-recovery-capabilities");
        request.setRemoteAddr("192.0.2.10");
        return request;
    }

    private boolean allowed(MockHttpServletRequest request, SecurityVerifyProperties properties) throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TaskRecoveryTransportFilter(properties).doFilter(request, response, (req, res) -> called.set(true));
        if (!called.get()) { assertThat(response.getStatus()).isEqualTo(403); }
        return called.get();
    }
}
