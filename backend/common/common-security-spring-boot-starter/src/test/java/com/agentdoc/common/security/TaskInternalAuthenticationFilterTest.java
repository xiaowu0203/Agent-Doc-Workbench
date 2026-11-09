package com.agentdoc.common.security;

import com.agentdoc.common.config.TaskInternalAuthProperties;
import com.agentdoc.common.constant.TaskInternalAuthConstant;
import com.agentdoc.common.filter.TaskInternalAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskInternalAuthenticationFilterTest {

    private final String secret = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingOrShortServerCredentialFailsClosedEvenWithCallerCredential() throws Exception {
        for (String configured : new String[]{"", "short"}) {
            TaskInternalAuthProperties properties = new TaskInternalAuthProperties();
            properties.setSecret(configured);
            var response = new MockHttpServletResponse();
            filter(properties).doFilter(request(basic()), response, (req, res) -> {
                throw new AssertionError("服务未配置凭据时不能进入签发");
            });
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getContentAsString()).doesNotContain(secret);
        }
    }

    @Test
    void validMachineIdentityIsRequestScopedAndCannotGrantUserRole() throws Exception {
        var response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        filter(configured()).doFilter(request(basic()), response, (req, res) -> {
            var identity = SecurityContextHolder.getContext().getAuthentication();
            assertThat(identity.getName()).isEqualTo(TaskInternalAuthConstant.CLIENT_ID);
            assertThat(identity.getCredentials()).isNull();
            assertThat(identity.getAuthorities()).extracting("authority")
                    .containsExactly(TaskInternalAuthConstant.AUTHORITY);
            invoked.set(true);
        });
        assertThat(invoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unencryptedRemoteConnectionRequiresExplicitTrustedNetwork() throws Exception {
        var request = request(basic());
        request.setSecure(false);
        request.setRemoteAddr("192.0.2.1");
        request.setLocalAddr("192.0.2.2");
        var response = new MockHttpServletResponse();
        filter(configured()).doFilter(request, response, (req, res) -> {
            throw new AssertionError("不能在未确认的明文网络接收机器凭据");
        });
        assertThat(response.getStatus()).isEqualTo(403);
        var properties = configured();
        properties.setTrustedEncryptedNetwork(true);
        AtomicBoolean invoked = new AtomicBoolean();
        filter(properties).doFilter(request, new MockHttpServletResponse(), (req, res) -> invoked.set(true));
        assertThat(invoked).isTrue();
    }

    @Test
    void malformedDuplicateAndBearerCredentialsDoNotReachSigning() throws Exception {
        for (String header : new String[]{"Basic !!!", "Bearer user-token", "Basic "}) {
            var response = new MockHttpServletResponse();
            filter(configured()).doFilter(request(header), response, (req, res) -> {
                throw new AssertionError("无效凭据不能进入签发");
            });
            assertThat(response.getStatus()).isEqualTo(401);
        }
        var request = request(basic());
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        var response = new MockHttpServletResponse();
        filter(configured()).doFilter(request, response, (req, res) -> {
            throw new AssertionError("不能混合机器和用户凭据");
        });
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void encodedMatrixAndDuplicateSlashAliasesRemainInsideTheMachineBoundary() throws Exception {
        for (String path : new String[]{"/api/%61uth/internal/task-capabilities",
                "/api/%2561uth/internal/task-capabilities", "/api/auth;v=1/internal/task-capabilities",
                "/api/auth//internal/task-capabilities", "/api/other/../auth/internal/task-capabilities"}) {
            var request = new MockHttpServletRequest("POST", path);
            request.setSecure(true);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token");
            var response = new MockHttpServletResponse();
            filter(configured()).doFilter(request, response, (req, res) -> {
                throw new AssertionError("编码路径不能绕过机器认证");
            });
            assertThat(response.getStatus()).as(path).isEqualTo(401);
        }
    }

    @Test
    void ordinaryUserRoutesAreNotConvertedToMachineAuthentication() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, basic());
        AtomicBoolean invoked = new AtomicBoolean();
        filter(configured()).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            invoked.set(true);
        });
        assertThat(invoked).isTrue();
    }

    @Test
    void downstreamFailureDoesNotLeaveMachineIdentityOnReusedThread() {
        assertThatThrownBy(() -> filter(configured()).doFilter(request(basic()), new MockHttpServletResponse(),
                (req, res) -> { throw new IOException("downstream failure"); }))
                .isInstanceOf(IOException.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private TaskInternalAuthProperties configured() {
        var properties = new TaskInternalAuthProperties();
        properties.setSecret(secret);
        return properties;
    }

    private TaskInternalAuthenticationFilter filter(TaskInternalAuthProperties properties) {
        return new TaskInternalAuthenticationFilter(properties, TaskInternalAuthConstant.SIGNING_PATHS);
    }

    private MockHttpServletRequest request(String authorization) {
        var request = new MockHttpServletRequest("POST", TaskInternalAuthConstant.SIGNING_PATHS.getFirst());
        request.setSecure(true);
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        return request;
    }

    private String basic() {
        return "Basic " + HttpHeaders.encodeBasicAuth(TaskInternalAuthConstant.CLIENT_ID, secret, StandardCharsets.UTF_8);
    }
}
