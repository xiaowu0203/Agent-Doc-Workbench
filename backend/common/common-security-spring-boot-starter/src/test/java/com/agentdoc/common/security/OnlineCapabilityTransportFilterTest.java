package com.agentdoc.common.security;

import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.filter.OnlineCapabilityTransportFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class OnlineCapabilityTransportFilterTest {
    @Test
    void rejectsCleartextAndForwardedHeaderBypassesIncludingEncodedPaths() throws Exception {
        for (String path : List.of("/api/auth/internal/online-capabilities", "/api/%61uth/internal/online-capabilities",
                "/api/auth;param=1/internal//online-capabilities", "/api/other/../auth/internal/online-capabilities")) {
            var request = request(path); request.addHeader("X-Forwarded-Proto", "https"); request.addHeader("X-Forwarded-For", "127.0.0.1");
            assertThat(allowed(request)).isFalse();
        }
    }

    @Test
    void dedicatedHeaderCannotMixIdentityOrEnterOrdinaryWritePath() throws Exception {
        var request = request("/api/auth/internal/online-capabilities"); request.setSecure(true);
        request.addHeader(OnlineCapabilityConstant.HEADER, "proof");
        assertThat(allowed(request)).isTrue();
        request.addHeader("Authorization", "Bearer user"); assertThat(allowed(request)).isFalse();
        request.removeHeader("Authorization"); request.addHeader("X-TASK-CAPABILITY", "agent"); assertThat(allowed(request)).isFalse();
        request.removeHeader("X-TASK-CAPABILITY"); request.setRequestURI("/api/task/tasks"); assertThat(allowed(request)).isFalse();
    }

    @Test
    void preservesOrdinaryRequestsAndAllowsActualLoopback() throws Exception {
        var request = request("/api/task/tasks"); assertThat(allowed(request)).isTrue();
        request.setRequestURI("/api/evaluation/internal/online-authorizations/11/human-proof");
        request.setRemoteAddr("127.0.0.1"); request.addHeader("Authorization", "Bearer user"); assertThat(allowed(request)).isTrue();
    }

    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("POST", path); request.setRemoteAddr("192.0.2.10"); return request;
    }
    private boolean allowed(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse(); var called = new AtomicBoolean();
        new OnlineCapabilityTransportFilter().doFilter(request, response, (req, res) -> called.set(true));
        if (!called.get()) { assertThat(response.getStatus()).isEqualTo(403); }
        return called.get();
    }
}
