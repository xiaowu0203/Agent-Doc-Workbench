package com.agentdoc.gateway.poc;

import com.agentdoc.common.utils.JsonUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.RefreshTokenReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.session.InMemoryWebSessionStore;
import org.springframework.web.server.session.WebSessionStore;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.agentdoc.gateway.poc.P0LocalBffPocTest.client;
import static com.agentdoc.gateway.poc.P0LocalBffPocTest.cookie;
import static com.agentdoc.gateway.poc.P0LocalBffPocTest.csrf;
import static com.agentdoc.gateway.poc.P0LocalBffPocTest.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 真实Keycloak + 真实Spring WebFlux链；HTTP驱动不替代真实浏览器的跨站/UI验收。 */
@EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_OIDC_POC", matches = "true")
class P0KeycloakBffPocTest {

    static final Path ROOT = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();
    static Map<?, ?> fixture;
    final AtomicBoolean bound = new AtomicBoolean(true);

    @BeforeAll
    static void fixture() throws IOException {
        fixture = JsonUtils.parse(Files.readString(ROOT.resolve(".codex-validation-store/v0.3.0-p0/runtime/oidc-poc.json")), Map.class);
    }

    @Test
    void realPkceCallbackStoresAuthorizedClientOnlyOnServerAndReturnsSharedProfile() throws Exception {
        InMemoryWebSessionStore store = new InMemoryWebSessionStore();
        try (Browser browser = new Browser()) {
            runner(store).run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean("localLogin");
                WebTestClient client = client(context);
                Login login = login(client, browser, true);
                assertThat(login.usedForm()).isTrue();
                EntityExchangeResult<byte[]> me = get(client, "/api/session", login.cookie());
                assertThat(me.getStatus()).isEqualTo(HttpStatus.OK);
                Map<?, ?> body = JsonUtils.parse(new String(me.getResponseBody(), StandardCharsets.UTF_8), Map.class);
                assertThat(body.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder("user", "expiresAt");
                assertThat(((Map<?, ?>) body.get("user")).get("id")).isEqualTo("1");
                var session = store.retrieveSession(login.cookie()).block(Duration.ofSeconds(5));
                assertThat(authorizedClient(session.getAttributes())).as("Session必须保存AuthorizedClient，不能只保存登录身份").isNotNull();
            });
        }
    }

    @Test
    void localLogoutKeepsSsoAndExplicitSsoLogoutRemovesIt() throws Exception {
        try (Browser browser = new Browser()) {
            runner(new InMemoryWebSessionStore()).run(context -> {
                WebTestClient client = client(context);
                Login first = login(client, browser, false);
                assertThat(logout(client, first.cookie(), "/api/session/logout").getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
                assertThat(get(client, "/api/session", first.cookie()).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                Login second = login(client, browser, false);
                assertThat(second.usedForm()).as("本地退出不应退出IdP SSO").isFalse();
                EntityExchangeResult<byte[]> exit = logout(client, second.cookie(), "/api/session/logout-sso");
                assertThat(exit.getStatus()).isEqualTo(HttpStatus.FOUND);
                browser.call("get", exit.getResponseHeaders().getLocation().toString());
                Login third = login(client, browser, false);
                assertThat(third.usedForm()).as("SSO退出后必须重新在IdP认证").isTrue();
            });
        }
    }

    @Test
    void wrongStateAndLocalPasswordEndpointCannotCreateOidcSession() throws Exception {
        try (Browser browser = new Browser()) {
            runner(new InMemoryWebSessionStore()).run(context -> {
                WebTestClient client = client(context);
                EntityExchangeResult<byte[]> anonymous = get(client, "/api/csrf", null);
                String session = cookie(anonymous);
                EntityExchangeResult<byte[]> start = get(client, "/oauth2/authorization/workbench", session);
                String callback = browser.call("authorize", start.getResponseHeaders().getLocation().toString()).get("callback").toString();
                String invalid = UriComponentsBuilder.fromUriString(callback).replaceQueryParam("state", "invalid-state")
                        .build(true).toUriString();
                assertThat(get(client, invalid, session).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                EntityExchangeResult<byte[]> current = get(client, "/api/csrf", null);
                EntityExchangeResult<byte[]> local = client.post().uri("/api/session/login")
                        .cookie(P0BffPocConfiguration.COOKIE, cookie(current)).header("X-CSRF-TOKEN", csrf(current))
                        .body(BodyInserters.fromFormData("username", "p0-user").with("password", fixture.get("password").toString()))
                        .exchange().expectBody().returnResult();
                assertThat(local.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(get(client, "/api/session", cookie(current)).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            });
        }
    }

    @Test
    void unboundIdentityInvalidatesCallbackSession() throws Exception {
        bound.set(false);
        try (Browser browser = new Browser()) {
            runner(new InMemoryWebSessionStore()).run(context -> {
                WebTestClient client = client(context);
                String anonymous = cookie(get(client, "/api/csrf", null));
                EntityExchangeResult<byte[]> start = get(client, "/oauth2/authorization/workbench", anonymous);
                String callback = browser.call("authorize", start.getResponseHeaders().getLocation().toString()).get("callback").toString();
                assertThat(get(client, callback, anonymous).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(get(client, "/api/session", anonymous).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            });
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_REDIS_POC", matches = "true")
    void encryptedRedisSessionAndAuthorizedClientSurviveContextAndConnectionRestart() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String namespace = "adw:p0:" + UUID.randomUUID();
        AtomicReference<Login> login = new AtomicReference<>();
        AtomicReference<String> access = new AtomicReference<>();
        try (Browser browser = new Browser(); P0RedisSessionStore first = new P0RedisSessionStore(namespace, key)) {
            try {
                runner(first.sessions).run(context -> {
                    login.set(login(client(context), browser, true));
                    var session = first.sessions.retrieveSession(login.get().cookie()).block(Duration.ofSeconds(5));
                    OAuth2AuthorizedClient savedClient = authorizedClient(session.getAttributes());
                    access.set(savedClient.getAccessToken().getTokenValue());
                    String refresh = savedClient.getRefreshToken().getTokenValue();
                    String redisKey = namespace + ":sessions:" + login.get().cookie();
                    var values = first.raw.<String, byte[]>opsForHash().values(redisKey).collectList().block(Duration.ofSeconds(5));
                    assertThat(values).isNotEmpty();
                    assertThat(values.stream().filter(value -> value.length > 0)
                            .allMatch(value -> new String(value, StandardCharsets.UTF_8).startsWith("v1:")))
                            .as("所有非空Session属性必须使用带版本的加密格式").isTrue();
                    assertThat(values.stream().map(value -> new String(value, StandardCharsets.ISO_8859_1))
                            .anyMatch(value -> value.contains(access.get()) || value.contains(refresh)
                                    || value.contains(fixture.get("webClientSecret").toString())))
                            .as("Redis不得保存原文Access/Refresh Token或客户端密钥").isFalse();
                    Duration ttl = first.raw.getExpire(redisKey).block(Duration.ofSeconds(5));
                    assertThat(ttl).isPositive().isLessThanOrEqualTo(P0BffPocConfiguration.WINDOW);
                });
                // 同一测试密钥保留，连接和上下文均重建，不复用内存Session/AuthorizedClient缓存。
                first.close();
                try (P0RedisSessionStore restarted = new P0RedisSessionStore(namespace, key)) {
                    runner(restarted.sessions).run(context -> {
                        WebTestClient client = client(context);
                        assertThat(get(client, "/api/session", login.get().cookie()).getStatus()).isEqualTo(HttpStatus.OK);
                        var recovered = restarted.sessions.retrieveSession(login.get().cookie()).block(Duration.ofSeconds(5));
                        assertThat(authorizedClient(recovered.getAttributes()).getAccessToken().getTokenValue().equals(access.get())).isTrue();
                        assertThat(logout(client, login.get().cookie(), "/api/session/logout").getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
                        assertThat(restarted.raw.hasKey(namespace + ":sessions:" + login.get().cookie()).block(Duration.ofSeconds(5))).isFalse();
                        assertThat(get(client, "/api/session", login.get().cookie()).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    });
                    P0RedisSessionStore.EncryptedSerializer serializer = new P0RedisSessionStore.EncryptedSerializer(key);
                    byte[] tampered = serializer.serialize("fixture");
                    tampered[3] = (byte) (tampered[3] == 'A' ? 'B' : 'A');
                    assertThatThrownBy(() -> serializer.deserialize(tampered)).isInstanceOf(SerializationException.class);
                    restarted.clearFixture();
                }
            } finally {
                // 包括测试失败路径：只清理本次随机namespace，连接关闭后重新连接清理。
                try (P0RedisSessionStore cleanup = new P0RedisSessionStore(namespace, key)) {
                    cleanup.clearFixture();
                }
            }
        }
    }

    private static OAuth2AuthorizedClient authorizedClient(Map<String, Object> attributes) {
        return attributes.values().stream().filter(Map.class::isInstance).map(value -> (Map<?, ?>) value)
                .flatMap(map -> map.values().stream()).filter(OAuth2AuthorizedClient.class::isInstance)
                .map(OAuth2AuthorizedClient.class::cast).findFirst().orElseThrow();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_REDIS_POC", matches = "true")
    void realRefreshRotationCanBeSavedAndRecoveredFromRedis() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String namespace = "adw:p0:" + UUID.randomUUID();
        try (Browser browser = new Browser(); P0RedisSessionStore redis = new P0RedisSessionStore(namespace, key)) {
            try {
                runner(redis.sessions).run(context -> {
                    WebTestClient client = client(context);
                    Login login = login(client, browser, false);
                    var session = redis.sessions.retrieveSession(login.cookie()).block(Duration.ofSeconds(5));
                    OAuth2AuthorizedClient original = authorizedClient(session.getAttributes());
                    Instant now = Instant.now();
                    var expired = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                            original.getAccessToken().getTokenValue(), now.minusSeconds(120), now.minusSeconds(60));
                    var toRefresh = new OAuth2AuthorizedClient(original.getClientRegistration(), original.getPrincipalName(),
                            expired, original.getRefreshToken());
                    var principal = session.<SecurityContext>getAttribute("SPRING_SECURITY_CONTEXT").getAuthentication();
                    var provider = new RefreshTokenReactiveOAuth2AuthorizedClientProvider();
                    var rotated = provider.authorize(OAuth2AuthorizationContext.withAuthorizedClient(toRefresh)
                            .principal(principal).build()).block(Duration.ofSeconds(15));
                    assertThat(rotated).isNotNull();
                    assertThat(rotated.getRefreshToken().getTokenValue().equals(original.getRefreshToken().getTokenValue())).isFalse();
                    var clientEntry = session.getAttributes().entrySet().stream()
                            .filter(entry -> entry.getValue() instanceof Map<?, ?> map
                                    && map.values().stream().anyMatch(OAuth2AuthorizedClient.class::isInstance))
                            .findFirst().orElseThrow();
                    Map<Object, Object> updated = new HashMap<>((Map<?, ?>) clientEntry.getValue());
                    Object clientKey = updated.entrySet().stream().filter(entry -> entry.getValue() instanceof OAuth2AuthorizedClient)
                            .findFirst().orElseThrow().getKey();
                    updated.put(clientKey, rotated);
                    session.getAttributes().put(clientEntry.getKey(), updated);
                    session.save().block(Duration.ofSeconds(5));
                    var recovered = redis.sessions.retrieveSession(login.cookie()).block(Duration.ofSeconds(5));
                    assertThat(authorizedClient(recovered.getAttributes()).getRefreshToken().getTokenValue()
                            .equals(rotated.getRefreshToken().getTokenValue())).isTrue();
                    assertThat(logout(client, login.cookie(), "/api/session/logout").getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
                });
            } finally {
                redis.clearFixture();
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_REDIS_POC", matches = "true")
    void logoutAfterRealProviderRefreshPreventsLateRedisWrite() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String namespace = "adw:p0:" + UUID.randomUUID();
        try (Browser browser = new Browser(); P0RedisSessionStore redis = new P0RedisSessionStore(namespace, key)) {
            try {
                runner(redis.sessions).run(context -> {
                    WebTestClient client = client(context);
                    Login login = login(client, browser, false);
                    var session = redis.sessions.retrieveSession(login.cookie()).block(Duration.ofSeconds(5));
                    OAuth2AuthorizedClient original = authorizedClient(session.getAttributes());
                    Instant now = Instant.now();
                    var expired = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                            original.getAccessToken().getTokenValue(), now.minusSeconds(120), now.minusSeconds(60));
                    var toRefresh = new OAuth2AuthorizedClient(original.getClientRegistration(), original.getPrincipalName(),
                            expired, original.getRefreshToken());
                    var principal = session.<SecurityContext>getAttribute("SPRING_SECURITY_CONTEXT").getAuthentication();
                    var entry = session.getAttributes().entrySet().stream()
                            .filter(item -> item.getValue() instanceof Map<?, ?> map
                                    && map.values().stream().anyMatch(OAuth2AuthorizedClient.class::isInstance))
                            .findFirst().orElseThrow();
                    Map<Object, Object> clients = new HashMap<>((Map<?, ?>) entry.getValue());
                    Object clientKey = clients.entrySet().stream().filter(item -> item.getValue() instanceof OAuth2AuthorizedClient)
                            .findFirst().orElseThrow().getKey();
                    CountDownLatch rotated = new CountDownLatch(1);
                    CountDownLatch resume = new CountDownLatch(1);
                    try (var worker = Executors.newSingleThreadExecutor()) {
                        var result = worker.submit(() -> {
                            var provider = new RefreshTokenReactiveOAuth2AuthorizedClientProvider();
                            var newClient = provider.authorize(OAuth2AuthorizationContext.withAuthorizedClient(toRefresh)
                                    .principal(principal).build()).block(Duration.ofSeconds(15));
                            if (newClient == null) { throw new IllegalStateException("refresh failed"); }
                            clients.put(clientKey, newClient);
                            byte[] ciphertext = new P0RedisSessionStore.EncryptedSerializer(key).serialize(clients);
                            rotated.countDown();
                            if (!resume.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("timeout"); }
                            byte[] attribute = ("sessionAttr:" + entry.getKey()).getBytes(StandardCharsets.UTF_8);
                            return redis.raw.execute(P0RedisLogoutRaceTest.SAVE_IF_PRESENT,
                                    List.of(namespace + ":sessions:" + login.cookie()),
                                    List.of(attribute, ciphertext)).single().block(Duration.ofSeconds(5));
                        });
                        assertThat(rotated.await(15, TimeUnit.SECONDS)).isTrue();
                        assertThat(logout(client, login.cookie(), "/api/session/logout").getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
                        resume.countDown();
                        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(0L);
                        assertThat(redis.sessions.retrieveSession(login.cookie()).block(Duration.ofSeconds(5))).isNull();
                    } catch (Exception error) {
                        throw new IllegalStateException("P0 refresh/logout race failed", error);
                    }
                });
            } finally {
                redis.clearFixture();
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_REDIS_POC", matches = "true")
    void refreshSaveFailureInvalidatesRedisSession() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String namespace = "adw:p0:" + UUID.randomUUID();
        try (Browser browser = new Browser(); P0RedisSessionStore redis = new P0RedisSessionStore(namespace, key)) {
            try {
                runner(redis.sessions).run(context -> {
                    WebTestClient client = client(context);
                    Login login = login(client, browser, false);
                    var session = redis.sessions.retrieveSession(login.cookie()).block(Duration.ofSeconds(5));
                    OAuth2AuthorizedClient original = authorizedClient(session.getAttributes());
                    Instant now = Instant.now();
                    var expired = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                            original.getAccessToken().getTokenValue(), now.minusSeconds(120), now.minusSeconds(60));
                    var toRefresh = new OAuth2AuthorizedClient(original.getClientRegistration(), original.getPrincipalName(),
                            expired, original.getRefreshToken());
                    var principal = session.<SecurityContext>getAttribute("SPRING_SECURITY_CONTEXT").getAuthentication();
                    var rotated = new RefreshTokenReactiveOAuth2AuthorizedClientProvider()
                            .authorize(OAuth2AuthorizationContext.withAuthorizedClient(toRefresh).principal(principal).build())
                            .block(Duration.ofSeconds(15));
                    assertThat(rotated).isNotNull();
                    assertThat(rotated.getRefreshToken().getTokenValue().equals(original.getRefreshToken().getTokenValue())).isFalse();

                    // 模拟轮换成功后保存失败；恢复Redis操作时删除会话，避免继续使用旧Refresh。
                    Mono<Void> failedSave = Mono.<Void>error(new IllegalStateException("fixture save failure"))
                            .onErrorResume(error -> session.invalidate().then(Mono.error(error)));
                    assertThatThrownBy(() -> failedSave.block(Duration.ofSeconds(5)))
                            .isInstanceOf(IllegalStateException.class).hasMessage("fixture save failure");
                    assertThat(redis.sessions.retrieveSession(login.cookie()).block(Duration.ofSeconds(5))).isNull();
                    assertThat(get(client, "/api/session", login.cookie()).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
            } finally {
                redis.clearFixture();
            }
        }
    }

    ReactiveWebApplicationContextRunner runner(WebSessionStore store) {
        return new ReactiveWebApplicationContextRunner().withUserConfiguration(P0BffPocConfiguration.class)
                .withPropertyValues("workbench.auth.mode=oidc", "poc.issuer=" + fixture.get("issuer"),
                        "poc.client-secret=" + fixture.get("webClientSecret"))
                .withBean(Clock.class, Clock::systemUTC).withBean(WebSessionStore.class, () -> store)
                .withBean(P0BffPocConfiguration.IdentityProjection.class, () -> authentication -> bound.get()
                        ? Mono.just(Map.of("id", "1", "nickname", "P0")) : Mono.error(new AccessDeniedException("unbound")));
    }

    static Login login(WebTestClient client, Browser browser, boolean remember) {
        String anonymous = cookie(get(client, "/api/csrf", null));
        EntityExchangeResult<byte[]> start = get(client, "/oauth2/authorization/workbench?remember=" + remember, anonymous);
        assertThat(start.getStatus()).isEqualTo(HttpStatus.FOUND);
        String location = start.getResponseHeaders().getLocation().toString();
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(query.containsKey("state") && query.containsKey("nonce")).isTrue();
        Map<?, ?> result = browser.call("authorize", location);
        assertThat(((Number) result.get("status")).intValue()).isEqualTo(302);
        EntityExchangeResult<byte[]> callback = get(client, result.get("callback").toString(), anonymous);
        assertThat(callback.getStatus()).as("OIDC failure code: %s", callback.getResponseHeaders().getFirst("X-Poc-Failure"))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(callback.getResponseCookies().getFirst(P0BffPocConfiguration.COOKIE).getMaxAge())
                .isEqualTo(remember ? P0BffPocConfiguration.WINDOW : Duration.ofSeconds(-1));
        String loggedIn = cookie(callback);
        assertThat(loggedIn.equals(anonymous)).isFalse();
        return new Login(loggedIn, Boolean.TRUE.equals(result.get("usedLoginForm")));
    }

    static EntityExchangeResult<byte[]> logout(WebTestClient client, String session, String path) {
        EntityExchangeResult<byte[]> csrf = get(client, "/api/csrf", session);
        return client.post().uri(path).cookie(P0BffPocConfiguration.COOKIE, session).header("X-CSRF-TOKEN", csrf(csrf))
                .exchange().expectBody().returnResult();
    }

    record Login(String cookie, boolean usedForm) { }

    static class Browser implements AutoCloseable {
        private final Process process;
        private final BufferedWriter input;
        private final BufferedReader output;

        Browser() throws IOException {
            process = new ProcessBuilder("python", ROOT.resolve("deploy/identity/PocBrowser.py").toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        }

        Map<?, ?> call(String action, String url) {
            try {
                input.write(JsonUtils.toJson(Map.of("action", action, "url", url)));
                input.newLine();
                input.flush();
                String line = output.readLine();
                if (line == null) { throw new IllegalStateException("PoC browser driver terminated"); }
                Map<?, ?> result = JsonUtils.parse(line, Map.class);
                assertThat(result.containsKey("error")).as("PoC browser request must succeed; sensitive response suppressed").isFalse();
                return result;
            } catch (IOException error) {
                throw new IllegalStateException("PoC browser driver failed");
            }
        }

        @Override
        public void close() throws Exception {
            input.write("{\"action\":\"stop\"}"); input.newLine(); input.flush(); input.close();
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); }
            output.close();
        }
    }
}
