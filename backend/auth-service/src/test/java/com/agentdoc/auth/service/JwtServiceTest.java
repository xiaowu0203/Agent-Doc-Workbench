package com.agentdoc.auth.service;

import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.auth.pojo.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

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
                10L, 20L, 30L, 40L, List.of("READ_FRAGMENT")));

        assertEquals("10", jwt.getSubject());
        assertEquals("AGENT", jwt.getClaimAsString("actorType"));
        assertEquals("agent", jwt.getClaimAsString("scope"));
        assertEquals(List.of("READ_FRAGMENT"), jwt.getClaimAsStringList("agentActions"));
    }
}
