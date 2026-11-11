package com.agentdoc.common.security;

import com.agentdoc.common.config.CommonSecurityAutoConfiguration;
import com.agentdoc.common.config.SecurityVerifyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class LocalIdentitySigningDomainTest {
    @Test
    void realIdentityJwksAcceptsOnlyHumanContractAndRejectsCapabilitySignatures() throws Exception {
        var identity = new RSAKeyGenerator(2048).keyID("local-identity-test").generate();
        var capability = new RSAKeyGenerator(2048).keyID("capability-test").generate();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint(server, "/identity", identity); endpoint(server, "/capability", capability); server.start();
        try {
            var properties = new SecurityVerifyProperties();
            properties.setIdentityJwksUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/identity");
            properties.setJwksUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/capability");
            var decoder = new CommonSecurityAutoConfiguration(new ObjectMapper()).businessJwtDecoder(properties);
            assertThat(decoder.decode(sign(identity, "adw-human+jwt", claims().build())).getSubject()).isEqualTo("42");
            for (var claims : List.of(claims().issuer("https://other.example").build(), claims().audience(List.of("other")).build(),
                    claims().claim("azp", "task-service").build(), claims().claim("scope", "user").build(),
                    claims().claim("purpose", "SERVICE_PROOF").build(), claims().claim("auth_version", "3").build(),
                    claims().expiresAt(Instant.now().plusSeconds(604900)).build())) {
                assertThatThrownBy(() -> decoder.decode(sign(identity, "adw-human+jwt", claims))).isInstanceOf(JwtException.class);
            }
            assertThatThrownBy(() -> decoder.decode(sign(identity, "JWT", claims().build()))).isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decoder.decode(sign(capability, "adw-human+jwt", claims().build()))).isInstanceOf(JwtException.class);
            var wrongSignature = new RSAKey.Builder(capability).keyID(identity.getKeyID()).build();
            assertThatThrownBy(() -> decoder.decode(sign(wrongSignature, "adw-human+jwt", claims().build()))).isInstanceOf(JwtException.class);
            var unsigned = new PlainJWT(new JWTClaimsSet.Builder().subject("42").build()).serialize();
            assertThatThrownBy(() -> decoder.decode(unsigned)).isInstanceOf(JwtException.class);
            var hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("adw-human+jwt")).keyID(identity.getKeyID()).build(),
                    new JWTClaimsSet.Builder().subject("42").build());
            hmac.sign(new MACSigner(new byte[32]));
            assertThatThrownBy(() -> decoder.decode(hmac.serialize())).isInstanceOf(JwtException.class);
        } finally { server.stop(0); }
    }

    @Test
    void missingUnsafeOrLegacyOnlyIdentityConfigurationCannotEnableLocalHumanDecoder() {
        for (String url : List.of("", "file:///tmp/key", "http://secret@example.invalid/key", "https://example.invalid/key?secret=x", "https://example.invalid/key#fragment")) {
            var properties = new SecurityVerifyProperties(); properties.setIdentityJwksUrl(url); properties.setJwksUrl("http://localhost/old-jwks");
            assertThatThrownBy(() -> new CommonSecurityAutoConfiguration(new ObjectMapper()).businessJwtDecoder(properties))
                    .isInstanceOf(IllegalStateException.class).hasNoCause();
        }
        assertThatThrownBy(() -> new CommonSecurityAutoConfiguration(new ObjectMapper()).businessJwtDecoder(new SecurityVerifyProperties()))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    @Test
    void identityJwksDoesNotFollowRedirects() throws Exception {
        var hits = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> { exchange.getResponseHeaders().set("Location", "/target"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        server.createContext("/target", exchange -> { hits.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
        try {
            var properties = new SecurityVerifyProperties(); properties.setIdentityJwksUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect");
            var decoder = new CommonSecurityAutoConfiguration(new ObjectMapper()).businessJwtDecoder(properties);
            var key = new RSAKeyGenerator(2048).keyID("local-identity-test").generate();
            assertThatThrownBy(() -> decoder.decode(sign(key, "adw-human+jwt", claims().build()))).isInstanceOf(JwtException.class);
            assertThat(hits.get()).isZero();
        } finally { server.stop(0); }
    }

    private void endpoint(HttpServer server, String path, RSAKey key) {
        server.createContext(path, exchange -> {
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
    }
    private String sign(RSAKey key, String type, JwtClaimsSet claims) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))).encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type(type).keyId(key.getKeyID()).build(), claims)).getTokenValue();
    }
    private JwtClaimsSet.Builder claims() {
        var now = Instant.now();
        return JwtClaimsSet.builder().issuer("urn:agent-doc-workbench:local-identity").subject("42").issuedAt(now).expiresAt(now.plusSeconds(300))
                .audience(List.of("workbench-api")).claim("azp", "workbench-web").claim("scope", "workbench:access").claim("actorType", "HUMAN")
                .claim("purpose", "HUMAN_ACCESS").claim("auth_version", 3L).claim("sid", UUID.randomUUID().toString());
    }
}
