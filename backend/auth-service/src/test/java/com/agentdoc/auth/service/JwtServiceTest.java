package com.agentdoc.auth.service;

import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.auth.pojo.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import com.agentdoc.common.enums.TaskExecutionMode;

import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtServiceTest {

    private final JwtProperties props = new JwtProperties("", "", Duration.ofMinutes(30), Duration.ofDays(7), "http://localhost:8081");
    private final JwtService jwtService = new JwtService(props);

    @Test
    void createAccessTokenCanBeDecodedAndVerified() throws Exception {
        RSAPublicKey publicKey = jwtService.getPublicKey();
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        UserEntity user = new UserEntity();
        user.setId(123L);
        user.setUsername("alice");
        user.setNickname("Alice");

        String token = jwtService.createAccessToken(user, List.of("PLATFORM_SUPER_ADMIN"));
        assertNotNull(token);

        Jwt jwt = decoder.decode(token);
        assertEquals("123", jwt.getSubject());
        assertEquals("alice", jwt.getClaimAsString("username"));
        assertEquals("Alice", jwt.getClaimAsString("nickname"));
        assertEquals(List.of("PLATFORM_SUPER_ADMIN"), jwt.getClaimAsStringList("platformRoles"));
        assertEquals("http://localhost:8081", jwt.getIssuer().toString());
    }

    @Test
    void refreshTokenIsOpaqueAndUrlSafe() {
        String token = jwtService.createRefreshToken();
        assertNotNull(token);
        assertEquals(true, token.matches("^[A-Za-z0-9_-]+$"));
    }

    @Test
    void createsTaskCapabilityTokenWithAgentScope() {
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtService.getPublicKey()).build();

        Jwt jwt = decoder.decode(jwtService.createTaskCapabilityToken(
                10L, 20L, 30L, 40L, TaskExecutionMode.ISOLATED.name(), 5L, "a".repeat(64),
                1, "b".repeat(64), "c".repeat(64), List.of("READ_FRAGMENT")));

        assertEquals("10", jwt.getSubject());
        assertEquals("AGENT", jwt.getClaimAsString("actorType"));
        assertEquals("agent", jwt.getClaimAsString("scope"));
        assertEquals(List.of("READ_FRAGMENT"), jwt.getClaimAsStringList("agentActions"));
        assertEquals(List.of("workbench-task-capability"), jwt.getAudience());
        assertEquals(TaskExecutionMode.ISOLATED.name(), jwt.getClaimAsString("executionMode"));
        assertEquals("a".repeat(64), jwt.getClaimAsString("documentContentSha256"));
        assertEquals("c".repeat(64), jwt.getClaimAsString("derivationRequestHash"));
    }

    @Test
    void createsEvaluationWorkerCapabilityBoundToRunAndTaskSet() {
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtService.getPublicKey()).build();

        Jwt jwt = decoder.decode(jwtService.createEvaluationWorkerCapabilityToken(
                11L, 22L, "d".repeat(64), 600L, List.of("BATCH_READ_TASK_STATUS")));

        assertEquals("evaluation-service", jwt.getSubject());
        assertEquals("SERVICE", jwt.getClaimAsString("actorType"));
        assertEquals("service", jwt.getClaimAsString("scope"));
        assertEquals(11L, ((Number) jwt.getClaim("runId")).longValue());
        assertEquals(22L, ((Number) jwt.getClaim("spaceId")).longValue());
        assertEquals("d".repeat(64), jwt.getClaimAsString("taskIdsHash"));
        assertEquals(List.of("BATCH_READ_TASK_STATUS"), jwt.getClaimAsStringList("workerActions"));
        assertEquals(List.of("task-service-internal"), jwt.getAudience());
    }
}
