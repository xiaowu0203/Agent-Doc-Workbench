package com.agentdoc.common.feign.interceptor;

import com.agentdoc.common.feign.dto.UserBatchQueryDTO;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.constant.TaskInternalAuthConstant;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.config.TaskInternalCallProperties;
import com.agentdoc.common.feign.context.AuthorizationContext;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityIssueDTO;
import com.agentdoc.common.utils.JsonUtils;
import feign.Feign;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskInternalCallAuthenticationTest {

    private final TaskInternalCallProperties properties = new TaskInternalCallProperties();
    private final List<Request> requests = new ArrayList<>();

    @AfterEach
    void clearContext() {
        TaskCapabilityContext.clear();
        AuthorizationContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void realFeignContractsUseDirectTargetsAndExcludeCurrentUserAndTaskCredentials() {
        properties.setSecret(Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32)));
        properties.setAuthUrl("http://localhost:18081");
        properties.setAgentUrl("http://localhost:18084");
        var incoming = new MockHttpServletRequest();
        incoming.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
        AuthorizationContext.set("Bearer background-token");
        TaskCapabilityContext.set("task-capability");

        var auth = client(AuthFeign.class);
        auth.issueTaskCapability(new TaskCapabilityIssueDTO(1L, 2L, 3L, 4L, "LIVE", 1L,
                "a".repeat(64), 1, "b".repeat(64), null, List.of("READ_FRAGMENT"), null));
        auth.issueEvaluationWorkerCapability(new EvaluationWorkerCapabilityIssueDTO(
                1L, 2L, "a".repeat(64), 300L, List.of("QUERY")));
        client(AgentFeign.class).getExecutionProfile(2L);

        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).url()).isEqualTo("http://localhost:18081/api/auth/internal/task-capabilities");
        assertThat(requests.get(1).url()).isEqualTo("http://localhost:18081/api/auth/internal/evaluation-worker-capabilities");
        assertThat(requests.get(2).url()).isEqualTo("http://localhost:18084/api/agent/internal/agents/2/execution-profile");
        String expected = "Basic " + HttpHeaders.encodeBasicAuth(
                TaskInternalAuthConstant.CLIENT_ID, properties.getSecret(), StandardCharsets.UTF_8);
        for (Request request : requests) {
            assertThat(request.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly(expected);
            assertThat(request.headers()).doesNotContainKey(HeaderConstants.X_TASK_CAPABILITY);
        }
    }

    @Test
    void nonMachineContractRetainsUserIdentityAndDoesNotReceiveServiceSecret() {
        AuthorizationContext.set("Bearer user-token");
        client(AuthFeign.class).queryUsers(new UserBatchQueryDTO(List.of(1L)));
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().url()).startsWith("http://localhost:19090/api/auth/internal/users/query");
        assertThat(requests.getFirst().headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer user-token");
    }

    @Test
    void identityLookupUsesOnlyExplicitBearerAndNeverForwardsTaskProof() {
        AuthorizationContext.set("Bearer different-user");
        TaskCapabilityContext.set("task-capability");
        client(AuthIdentityFeign.class).currentIdentity("Bearer verified-user");
        client(AuthIdentityFeign.class).checkPlatformRole("Bearer verified-user", "PLATFORM_SUPER_ADMIN");
        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst().url()).isEqualTo("http://localhost:19090/api/auth/identity");
        assertThat(requests.getFirst().headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer verified-user");
        assertThat(requests.getFirst().headers()).doesNotContainKey(HeaderConstants.X_TASK_CAPABILITY);
        assertThat(requests.get(1).headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer verified-user");
        assertThat(requests.get(1).headers()).doesNotContainKey(HeaderConstants.X_TASK_CAPABILITY);
    }

    @Test
    void identityLookupRejectsUntrustedTransportAndNonBearer() {
        var feign = Feign.builder().contract(new SpringMvcContract())
                .decoder((response, type) -> Result.ok("fixture"))
                .requestInterceptor(new AuthHeaderForwardInterceptor(properties))
                .client((request, options) -> { throw new AssertionError("不得发送请求"); })
                .target(AuthIdentityFeign.class, "http://untrusted-host:8081");
        assertThatThrownBy(() -> feign.currentIdentity("Bearer verified-user")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client(AuthIdentityFeign.class).currentIdentity("Basic fixture")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingSecretOrUntrustedTransportFailsBeforeNetworkAndNeverFallsBackToUser() {
        var auth = client(AuthFeign.class);
        var request = new EvaluationWorkerCapabilityIssueDTO(1L, 2L, "a".repeat(64), 300L, List.of("QUERY"));
        AuthorizationContext.set("Bearer user-token");
        assertThatThrownBy(() -> auth.issueEvaluationWorkerCapability(request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("缺少独立机器凭据");
        properties.setSecret(Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32)));
        properties.setAuthUrl("http://auth-service:8081");
        assertThatThrownBy(() -> auth.issueEvaluationWorkerCapability(request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("受信加密网络");
        assertThat(requests).isEmpty();
    }

    private <T> T client(Class<T> type) {
        return Feign.builder().contract(new SpringMvcContract())
                .encoder((value, bodyType, template) -> template.body(JsonUtils.toJson(value)))
                .decoder((response, bodyType) -> Result.ok("fixture"))
                .requestInterceptor(new AuthHeaderForwardInterceptor(properties))
                .client((request, options) -> {
                    requests.add(request);
                    return Response.builder().request(request).status(200).reason("OK")
                            .headers(Map.of()).body("{}", StandardCharsets.UTF_8).build();
                }).target(type, "http://localhost:19090");
    }
}
