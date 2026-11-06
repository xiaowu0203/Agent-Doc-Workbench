package com.agentdoc.gateway.poc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证测试原型在两个独立Java进程间恢复加密会话，不代表生产Gateway重启验收。 */
@EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_REDIS_POC", matches = "true")
class P0RedisProcessRestartTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void separateProcessRestoresSessionAndAuthorizedClient() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String namespace = "adw:p0:" + UUID.randomUUID();
        try {
            String session = run("write", namespace, key, "");
            assertThat(session).isNotBlank();
            assertThat(run("read", namespace, key, session)).isEqualTo("restored");
        } finally {
            try (var store = new P0RedisSessionStore(namespace, key)) { store.clearFixture(); }
        }
    }

    private String run(String mode, String namespace, byte[] key, String session) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var builder = new ProcessBuilder(java, "-cp", System.getProperty("surefire.test.class.path"),
                P0RedisProcessRestartTest.class.getName(), mode);
        builder.environment().put("P0_PROCESS_NAMESPACE", namespace);
        builder.environment().put("P0_PROCESS_KEY", Base64.getEncoder().encodeToString(key));
        builder.environment().put("P0_PROCESS_SESSION", session);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).isZero();
            return output.lines().filter(line -> line.startsWith("P0_RESULT="))
                    .map(line -> line.substring("P0_RESULT=".length())).findFirst().orElseThrow();
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); }
        }
    }

    public static void main(String[] args) {
        String namespace = System.getenv("P0_PROCESS_NAMESPACE");
        byte[] key = Base64.getDecoder().decode(System.getenv("P0_PROCESS_KEY"));
        try (var store = new P0RedisSessionStore(namespace, key)) {
            if ("write".equals(args[0])) {
                var session = store.sessions.createWebSession().block(TIMEOUT);
                var registration = ClientRegistration.withRegistrationId("fixture").clientId("fixture")
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri("http://localhost/callback").authorizationUri("http://localhost/authorize")
                        .tokenUri("http://localhost/token").build();
                Instant now = Instant.now();
                var access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fixture-access", now, now.plusSeconds(300));
                session.getAttributes().put("p0-client", new OAuth2AuthorizedClient(registration, "fixture", access,
                        new OAuth2RefreshToken("fixture-refresh", now)));
                session.start();
                session.save().block(TIMEOUT);
                System.out.println("P0_RESULT=" + session.getId());
            } else {
                var session = store.sessions.retrieveSession(System.getenv("P0_PROCESS_SESSION")).block(TIMEOUT);
                OAuth2AuthorizedClient client = session.getAttribute("p0-client");
                if (client == null || !"fixture-access".equals(client.getAccessToken().getTokenValue())
                        || !"fixture-refresh".equals(client.getRefreshToken().getTokenValue())) {
                    throw new IllegalStateException("session recovery failed");
                }
                System.out.println("P0_RESULT=restored");
            }
        }
    }
}
