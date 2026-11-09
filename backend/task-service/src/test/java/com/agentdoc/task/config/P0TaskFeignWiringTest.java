package com.agentdoc.task.config;

import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.config.FeignAutoConfiguration;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用Task的Feign自动装配和本地HTTP夹具验证实际注入与直连。 */
@SpringBootTest(classes = P0TaskFeignWiringTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class P0TaskFeignWiringTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32));
    private static final String EXPECTED = "Basic " + Base64.getEncoder().encodeToString(
            ("task-service:" + SECRET).getBytes(StandardCharsets.UTF_8));
    private static final AtomicInteger ACCEPTED = new AtomicInteger();
    private static final HttpServer SERVER = createServer();

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(FeignAutoConfiguration.class)
    static class App { }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + SERVER.getAddress().getPort();
        registry.add("agent-doc.task-internal-auth.secret", () -> SECRET);
        registry.add("agent-doc.task-internal-auth.auth-url", () -> url);
        registry.add("agent-doc.task-internal-auth.agent-url", () -> url);
        registry.add("agent-doc.feign.gateway-url", () -> "http://localhost:1");
    }

    @AfterAll
    static void close() { SERVER.stop(0); }

    @Autowired private AuthFeign auth;
    @Autowired private AgentFeign agent;

    @Test
    void autowiredFeignClientsUseTaskCredentialAndDirectTarget() {
        var worker = auth.issueEvaluationWorkerCapability(new EvaluationWorkerCapabilityIssueDTO(
                9101L, 9102L, "a".repeat(64), 300L, List.of("QUERY")));
        var task = auth.issueTaskCapability(new TaskCapabilityIssueDTO(9001L, 9002L, 9003L, 9004L, "LIVE", 1L,
                "a".repeat(64), 1, "b".repeat(64), null, List.of("READ_FRAGMENT"), null));
        var profile = agent.getExecutionProfile(9001L);
        assertThat(worker.code()).isZero();
        assertThat(task.code()).isZero();
        assertThat(profile.code()).isZero();
        assertThat(profile.data().agentId()).isEqualTo(9001L);
        assertThat(ACCEPTED).hasValue(3);
    }

    private static HttpServer createServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                boolean correctPath = exchange.getRequestURI().getPath().equals("/api/auth/internal/evaluation-worker-capabilities")
                        || exchange.getRequestURI().getPath().equals("/api/auth/internal/task-capabilities")
                        || exchange.getRequestURI().getPath().equals("/api/agent/internal/agents/9001/execution-profile");
                boolean allowed = correctPath && EXPECTED.equals(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] body = (exchange.getRequestURI().getPath().contains("execution-profile")
                        ? "{\"code\":0,\"message\":\"success\",\"data\":{\"agentId\":9001}}"
                        : "{\"code\":0,\"message\":\"success\",\"data\":\"fixture\"}").getBytes(StandardCharsets.UTF_8);
                if (allowed) { ACCEPTED.incrementAndGet(); }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(allowed ? 200 : 401, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            return server;
        } catch (IOException error) {
            throw new IllegalStateException("测试HTTP服务启动失败", error);
        }
    }
}
