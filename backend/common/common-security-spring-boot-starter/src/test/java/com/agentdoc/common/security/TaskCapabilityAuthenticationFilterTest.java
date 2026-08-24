package com.agentdoc.common.security;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.context.TaskCapabilityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskCapabilityAuthenticationFilterTest {

    @AfterEach
    void clearContexts() {
        TaskCapabilityContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesCapabilityDuringRequestAndClearsItAfterwards() throws Exception {
        String token = "task-capability";
        TaskCapabilityVerifier verifier = mock(TaskCapabilityVerifier.class);
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("agent")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(verifier.verify(token)).thenReturn(jwt);
        TaskCapabilityAuthenticationFilter filter = new TaskCapabilityAuthenticationFilter(verifier);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderConstants.X_TASK_CAPABILITY, token);
        Authentication originalAuthentication = mock(Authentication.class);
        SecurityContext originalContext = SecurityContextHolder.createEmptyContext();
        originalContext.setAuthentication(originalAuthentication);
        SecurityContextHolder.setContext(originalContext);

        filter.doFilter(request, new MockHttpServletResponse(), (req, response) -> {
            assertThat(TaskCapabilityContext.current()).isEqualTo(token);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        });

        assertThat(TaskCapabilityContext.current()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(originalAuthentication);
    }

    @Test
    void propagatesDownstreamExceptionAndRestoresContext() {
        String token = "task-capability";
        TaskCapabilityVerifier verifier = mock(TaskCapabilityVerifier.class);
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("agent")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(verifier.verify(token)).thenReturn(jwt);
        TaskCapabilityAuthenticationFilter filter = new TaskCapabilityAuthenticationFilter(verifier);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderConstants.X_TASK_CAPABILITY, token);
        Authentication originalAuthentication = mock(Authentication.class);
        SecurityContext originalContext = SecurityContextHolder.createEmptyContext();
        originalContext.setAuthentication(originalAuthentication);
        SecurityContextHolder.setContext(originalContext);

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, response) -> {
                    throw new IllegalStateException("downstream failure");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("downstream failure");

        assertThat(TaskCapabilityContext.current()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(originalAuthentication);
    }
}
