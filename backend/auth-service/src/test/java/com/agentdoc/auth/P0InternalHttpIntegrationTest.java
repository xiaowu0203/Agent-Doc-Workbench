package com.agentdoc.auth;

import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.service.JwtService;
import com.agentdoc.auth.support.LegacyHumanCredentialFixture;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityIssueDTO;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.config.TaskInternalCallProperties;
import com.agentdoc.common.feign.context.AuthorizationContext;
import com.agentdoc.common.feign.interceptor.AuthHeaderForwardInterceptor;
import com.agentdoc.common.utils.JsonUtils;
import feign.Feign;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实本机HTTP栈验证过渡入口，测试内H2和临时凭据，不访问业务库。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class P0InternalHttpIntegrationTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32));
    private static final String HASH = "a".repeat(64);
    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("agent-doc.task-internal-auth.secret", () -> SECRET);
    }

    @Test
    void anonymousAndUserCannotSignWhileTaskMachineCan() throws Exception {
        var user = new UserEntity();
        user.setId(9901L);
        user.setUsername("p0-fixture");
        String userToken = LegacyHumanCredentialFixture.issue(jwtService, user);
        var request = new TaskCapabilityIssueDTO(9001L, 9002L, 9003L, 9004L,
                TaskExecutionMode.LIVE.name(), 0L, HASH, 1, HASH, null,
                List.of(JwtConstant.ACTION_READ_FRAGMENT), null);
        var worker = new EvaluationWorkerCapabilityIssueDTO(9101L, 9102L, HASH, 300L, List.of("QUERY"));
        for (var entry : Map.of("/api/auth/internal/task-capabilities", request,
                "/api/auth/internal/evaluation-worker-capabilities", worker).entrySet()) {
            assertThat(post(entry.getKey(), null, entry.getValue())).isEqualTo(401);
            assertThat(post(entry.getKey(), "Bearer " + userToken, entry.getValue())).isEqualTo(401);
            assertThat(post(entry.getKey(), "Basic " + HttpHeaders.encodeBasicAuth("task-service", SECRET + "wrong", StandardCharsets.UTF_8), entry.getValue())).isEqualTo(401);
            assertThat(post(entry.getKey(), "Basic " + HttpHeaders.encodeBasicAuth("task-service", SECRET, StandardCharsets.UTF_8), entry.getValue())).isEqualTo(200);
        }
    }

    @Test
    void taskFeignUsesIndependentCredentialOverRealHttp() {
        var properties = new TaskInternalCallProperties();
        properties.setSecret(SECRET);
        properties.setAuthUrl("http://localhost:" + port);
        var auth = Feign.builder().contract(new SpringMvcContract())
                .encoder((value, type, template) -> template.header("Content-Type", "application/json")
                        .body(JsonUtils.toJson(value)))
                .decoder((response, type) -> JsonUtils.parse(
                        new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8), Result.class))
                .requestInterceptor(new AuthHeaderForwardInterceptor(properties))
                .target(AuthFeign.class, "http://localhost:1");
        try {
            AuthorizationContext.set("Bearer unrelated-user");
            var result = auth.issueEvaluationWorkerCapability(new EvaluationWorkerCapabilityIssueDTO(
                    9101L, 9102L, HASH, 300L, List.of("QUERY")));
            assertThat(result.code()).isZero();
            assertThat(result.data()).isNotNull();
        } finally {
            AuthorizationContext.clear();
        }
    }

    private int post(String path, String authorization, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body)));
        if (authorization != null) { builder.header("Authorization", authorization); }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
