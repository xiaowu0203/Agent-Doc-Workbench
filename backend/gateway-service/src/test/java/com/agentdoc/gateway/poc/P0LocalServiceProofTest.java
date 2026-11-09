package com.agentdoc.gateway.poc;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.agentdoc.common.utils.JsonUtils;
import com.sun.net.httpserver.HttpServer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 测试用受限服务证明；本地夹具不依赖IdP，跨模式负例按开关连接Keycloak。 */
class P0LocalServiceProofTest {
    @Test
    void localMachineCredentialCanObtainRestrictedProofOverHttp() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("urn:agentdoc:local"));
        byte[] secretBytes = new byte[32];
        new SecureRandom().nextBytes(secretBytes);
        String secret = Base64.getEncoder().encodeToString(secretBytes);
        String expected = "Basic " + Base64.getEncoder().encodeToString(("task-service:" + secret).getBytes(StandardCharsets.UTF_8));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/fixture/issue", exchange -> {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            boolean valid = "POST".equals(exchange.getRequestMethod()) && header != null
                    && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
            if (!valid) { exchange.sendResponseHeaders(401, -1); exchange.close(); return; }
            Instant now = Instant.now();
            var claims = JwtClaimsSet.builder().issuer("urn:agentdoc:local").subject("task-service")
                    .audience(List.of("workbench-auth-internal")).issuedAt(now).expiresAt(now.plusSeconds(300))
                    .claim("actorType", "SERVICE").claim("scope", "capability:task:issue").build();
            byte[] token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, token.length);
            try (var output = exchange.getResponseBody()) { output.write(token); }
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/fixture/issue");
            HttpClient http = HttpClient.newHttpClient();
            var wrong = HttpRequest.newBuilder(endpoint).header("Authorization", expected + "wrong")
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            assertThat(http.send(wrong, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
            var valid = HttpRequest.newBuilder(endpoint).header("Authorization", expected)
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            var issued = http.send(valid, HttpResponse.BodyHandlers.ofString());
            assertThat(issued.statusCode()).isEqualTo(200);
            Jwt verified = decoder.decode(issued.body());
            assertThat(verified.getSubject()).isEqualTo("task-service");
            assertThat(verified.getAudience()).containsExactly("workbench-auth-internal");
            assertThat(verified.getClaimAsString("actorType")).isEqualTo("SERVICE");
            assertThat(verified.getClaimAsString("scope")).isEqualTo("capability:task:issue");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_OIDC_POC", matches = "true")
    void localAndRealKeycloakMachineTokensCannotCrossSignatureDomains() throws Exception {
        var fixture = JsonUtils.parse(Files.readString(P0KeycloakBffPocTest.ROOT.resolve(
                ".codex-validation-store/v0.3.0-p0/runtime/oidc-poc.json")), Map.class);
        String issuer = fixture.get("issuer").toString();
        String credentials = fixture.get("taskClientId") + ":" + fixture.get("taskClientSecret");
        var request = HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String oidcToken = JsonUtils.parse(response.body(), Map.class).get("access_token").toString();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        Instant now = Instant.now();
        var localClaims = JwtClaimsSet.builder().issuer("urn:agentdoc:local").subject("task-service")
                .audience(List.of("workbench-auth-internal")).issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("actorType", "SERVICE").claim("scope", "capability:task:issue").build();
        String localToken = encoder.encode(JwtEncoderParameters.from(localClaims)).getTokenValue();
        var localDecoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
        var oidcDecoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs").build();
        assertThat(localDecoder.decode(localToken).getSubject()).isEqualTo("task-service");
        assertThat(oidcDecoder.decode(oidcToken).getAudience()).contains("workbench-auth-internal");
        assertThatThrownBy(() -> localDecoder.decode(oidcToken)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> oidcDecoder.decode(localToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void serviceProofRejectsOtherIssuersAudiencesClientsScopesAndActors() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
        OAuth2TokenValidator<Jwt> identity = jwt -> jwt.getAudience().equals(List.of("workbench-auth-internal"))
                && "task-service".equals(jwt.getSubject()) && "SERVICE".equals(jwt.getClaimAsString("actorType"))
                && "capability:task:issue".equals(jwt.getClaimAsString("scope"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("urn:agentdoc:local"), identity));
        Instant now = Instant.now();
        Function<JwtClaimsSet, String> sign = claims -> encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        Function<JwtClaimsSet.Builder, JwtClaimsSet.Builder> standard = builder -> builder
                .issuer("urn:agentdoc:local").subject("task-service").audience(List.of("workbench-auth-internal"))
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("actorType", "SERVICE").claim("scope", "capability:task:issue");
        assertThat(decoder.decode(sign.apply(standard.apply(JwtClaimsSet.builder()).build())).getSubject()).isEqualTo("task-service");
        var invalid = List.of(
                standard.apply(JwtClaimsSet.builder()).issuer("http://localhost:18090/realms/agent-doc-workbench-p0").build(),
                standard.apply(JwtClaimsSet.builder()).audience(List.of("workbench-api")).build(),
                standard.apply(JwtClaimsSet.builder()).subject("other-service").build(),
                standard.apply(JwtClaimsSet.builder()).claim("scope", "workbench:access").build(),
                standard.apply(JwtClaimsSet.builder()).claim("actorType", "HUMAN").build(),
                standard.apply(JwtClaimsSet.builder()).claim("actorType", "AGENT").build(),
                standard.apply(JwtClaimsSet.builder()).issuedAt(now.minusSeconds(240)).expiresAt(now.minusSeconds(120)).build());
        for (var claims : invalid) {
            String token = sign.apply(claims);
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }
    }
}
