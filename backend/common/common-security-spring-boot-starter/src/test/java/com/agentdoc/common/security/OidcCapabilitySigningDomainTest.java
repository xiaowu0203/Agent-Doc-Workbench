package com.agentdoc.common.security;

import com.agentdoc.common.config.SecurityVerifyProperties;
import com.agentdoc.common.config.TaskCapabilitySecurityAutoConfiguration;
import com.agentdoc.common.constant.JwtConstant;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OidcCapabilitySigningDomainTest {
    @Test
    void oidcHumanDecoderDoesNotReplaceBusinessCapabilityKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("p0-fixture").build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (var output = exchange.getResponseBody()) { output.write(jwks); }
        });
        server.start();
        try {
            var properties = new SecurityVerifyProperties();
            properties.setJwksUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
            var environment = new MockEnvironment().withProperty("workbench.auth.mode", "oidc");
            JwtDecoder oidcHumanDecoder = token -> { throw new JwtException("wrong signing domain"); };
            var config = new TaskCapabilitySecurityAutoConfiguration();
            var taskVerifier = config.taskCapabilityVerifier(oidcHumanDecoder, properties, environment);
            var workerVerifier = config.evaluationWorkerCapabilityVerifier(oidcHumanDecoder, properties, environment);
            Instant now = Instant.now();
            var task = JwtClaimsSet.builder().subject("1").issuedAt(now).expiresAt(now.plusSeconds(300))
                    .audience(List.of(JwtConstant.TASK_CAPABILITY_AUDIENCE)).claim("actorType", "AGENT").claim("scope", "agent").build();
            var worker = JwtClaimsSet.builder().subject("evaluation-service").issuedAt(now).expiresAt(now.plusSeconds(300))
                    .audience(List.of(JwtConstant.EVALUATION_WORKER_CAPABILITY_AUDIENCE))
                    .claim("actorType", "SERVICE").claim("scope", "service").claim("service", "evaluation-service").build();
            assertThat(taskVerifier.verify(encoder.encode(JwtEncoderParameters.from(task)).getTokenValue()).getSubject()).isEqualTo("1");
            assertThat(workerVerifier.verify(encoder.encode(JwtEncoderParameters.from(worker)).getTokenValue()).getSubject()).isEqualTo("evaluation-service");
        } finally {
            server.stop(0);
        }
    }
}
