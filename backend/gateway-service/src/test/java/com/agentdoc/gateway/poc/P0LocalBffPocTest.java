package com.agentdoc.gateway.poc;

import com.agentdoc.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.session.InMemoryWebSessionStore;
import org.springframework.web.server.session.WebSessionStore;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实WebFlux过滤链的local原型；账号/投影由独立Auth夹具提供，不连接IdP或业务库。 */
class P0LocalBffPocTest {

    private final String password = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32));
    private final AtomicBoolean bound = new AtomicBoolean(true);
    private final MutableClock clock = new MutableClock();

    @Test
    void defaultLocalHasNoOidcBeansAndUsesOnlyCookieSessionWithCsrf() {
        runner(null, store()).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ReactiveClientRegistrationRepository.class);
            WebTestClient client = client(context);
            assertThat(get(client, "/api/auth/config", null).getStatus()).isEqualTo(HttpStatus.OK);
            assertThat(get(client, "/oauth2/authorization/workbench", null).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(get(client, "/api/session", null).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            EntityExchangeResult<byte[]> anonymous = get(client, "/api/csrf", null);
            String oldCookie = cookie(anonymous);
            EntityExchangeResult<byte[]> noCsrf = client.post().uri("/api/session/login").cookie(P0BffPocConfiguration.COOKIE, oldCookie)
                    .body(BodyInserters.fromFormData("username", "p0-user").with("password", password))
                    .exchange().expectBody().returnResult();
            assertThat(noCsrf.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            EntityExchangeResult<byte[]> login = login(client, anonymous, false, password);
            assertThat(login.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
            ResponseCookie session = login.getResponseCookies().getFirst(P0BffPocConfiguration.COOKIE);
            assertThat(session.isHttpOnly()).isTrue();
            assertThat(session.getSameSite()).isEqualTo("Lax");
            assertThat(session.getPath()).isEqualTo("/");
            assertThat(session.getMaxAge().isNegative()).isTrue();
            assertThat(session.getValue().equals(oldCookie)).as("登录必须更换匿名会话ID").isFalse();
            assertThat(get(client, "/api/session", oldCookie).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            EntityExchangeResult<byte[]> me = get(client, "/api/session", session.getValue());
            assertThat(me.getStatus()).isEqualTo(HttpStatus.OK);
            Map<?, ?> body = JsonUtils.parse(new String(me.getResponseBody(), StandardCharsets.UTF_8), Map.class);
            assertThat(body.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder("user", "expiresAt");
            assertThat(((Map<?, ?>) body.get("user")).get("id")).isEqualTo("1");
        });
    }

    @Test
    void wrongCredentialsAndUnboundBusinessIdentityCannotCreateUsableSession() {
        runner("local", store()).run(context -> {
            WebTestClient client = client(context);
            EntityExchangeResult<byte[]> anonymous = get(client, "/api/csrf", null);
            assertThat(login(client, anonymous, false, password + "wrong").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            bound.set(false);
            EntityExchangeResult<byte[]> unbound = login(client, anonymous, false, password);
            assertThat(unbound.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(get(client, "/api/session", cookie(anonymous)).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });
    }

    @Test
    void rememberCookieAndActiveSessionCannotPassOriginalAbsoluteDeadline() {
        runner("local", store()).run(context -> {
            WebTestClient client = client(context);
            EntityExchangeResult<byte[]> login = login(client, get(client, "/api/csrf", null), true, password);
            assertThat(login.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(login.getResponseCookies().getFirst(P0BffPocConfiguration.COOKIE).getMaxAge())
                    .isEqualTo(P0BffPocConfiguration.WINDOW);
            String session = cookie(login);
            clock.advance(Duration.ofDays(6));
            assertThat(get(client, "/api/session", session).getStatus()).isEqualTo(HttpStatus.OK);
            clock.advance(Duration.ofDays(2));
            assertThat(get(client, "/api/session", session).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });
    }

    @Test
    void logoutIsCsrfProtectedAndInvalidatesOldSession() {
        runner("local", store()).run(context -> {
            WebTestClient client = client(context);
            String session = cookie(login(client, get(client, "/api/csrf", null), false, password));
            EntityExchangeResult<byte[]> csrf = get(client, "/api/csrf", session);
            assertThat(client.post().uri("/api/session/logout").cookie(P0BffPocConfiguration.COOKIE, session)
                    .exchange().expectBody().returnResult().getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(client.post().uri("/api/session/logout").cookie(P0BffPocConfiguration.COOKIE, session)
                    .header("X-CSRF-TOKEN", csrf(csrf)).exchange().expectBody().returnResult().getStatus())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(get(client, "/api/session", session).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });
    }

    @Test
    void startupRejectsUnknownModeAndMissingOidcConfiguration() {
        runner("invalid-mode", store()).run(context -> assertThat(context).hasFailed());
        runner("oidc", store()).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void retainedSessionIsRejectedAfterChangingModeEvenWithSharedStore() {
        InMemoryWebSessionStore shared = store();
        AtomicReference<String> savedCookie = new AtomicReference<>();
        runner("local", shared).run(context -> {
            WebTestClient client = client(context);
            savedCookie.set(cookie(login(client, get(client, "/api/csrf", null), false, password)));
        });
        runner("oidc", shared).withPropertyValues("poc.issuer=http://localhost:1/absent", "poc.client-secret=" + password)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    WebTestClient client = client(context);
                    assertThat(get(client, "/api/session", savedCookie.get()).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    EntityExchangeResult<byte[]> csrf = get(client, "/api/csrf", null);
                    assertThat(login(client, csrf, false, password).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    private ReactiveWebApplicationContextRunner runner(String mode, WebSessionStore store) {
        ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
                .withUserConfiguration(P0BffPocConfiguration.class).withBean(Clock.class, () -> clock)
                .withBean(WebSessionStore.class, () -> store)
                .withBean(P0BffPocConfiguration.IdentityProjection.class, () -> authentication -> bound.get()
                        ? Mono.just(Map.of("id", "1", "nickname", "P0")) : Mono.error(new AccessDeniedException("unbound")));
        if (mode != null) {
            runner = runner.withPropertyValues("workbench.auth.mode=" + mode);
        }
        if (mode == null || mode.equals("local")) {
            runner = runner.withBean("pocLocalAuthenticator", ReactiveAuthenticationManager.class, () -> {
                BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                UserDetailsRepositoryReactiveAuthenticationManager backend = new UserDetailsRepositoryReactiveAuthenticationManager(
                        new MapReactiveUserDetailsService(User.withUsername("p0-user").password(encoder.encode(password))
                                .authorities("IDENTITY").build()));
                backend.setPasswordEncoder(encoder);
                return backend;
            });
        }
        return runner;
    }

    private InMemoryWebSessionStore store() {
        InMemoryWebSessionStore store = new InMemoryWebSessionStore();
        store.setClock(clock);
        return store;
    }

    static WebTestClient client(ApplicationContext context) {
        return WebTestClient.bindToApplicationContext(context).configureClient().baseUrl("http://localhost:18091")
                .responseTimeout(Duration.ofSeconds(20)).build();
    }

    static EntityExchangeResult<byte[]> get(WebTestClient client, String path, String cookie) {
        WebTestClient.RequestHeadersUriSpec<?> uri = client.get();
        WebTestClient.RequestHeadersSpec<?> request = path.startsWith("http") ? uri.uri(URI.create(path)) : uri.uri(path);
        if (cookie != null) {
            request.cookie(P0BffPocConfiguration.COOKIE, cookie);
        }
        return request.exchange().expectBody().returnResult();
    }

    private EntityExchangeResult<byte[]> login(WebTestClient client, EntityExchangeResult<byte[]> csrf,
                                               boolean remember, String suppliedPassword) {
        return client.post().uri("/api/session/login").cookie(P0BffPocConfiguration.COOKIE, cookie(csrf))
                .header("X-CSRF-TOKEN", csrf(csrf)).body(BodyInserters.fromFormData("username", "p0-user")
                        .with("password", suppliedPassword).with("remember", Boolean.toString(remember)))
                .exchange().expectBody().returnResult();
    }

    static String cookie(EntityExchangeResult<byte[]> result) {
        return result.getResponseCookies().getFirst(P0BffPocConfiguration.COOKIE).getValue();
    }

    static String csrf(EntityExchangeResult<byte[]> result) {
        return JsonUtils.parse(new String(result.getResponseBody(), StandardCharsets.UTF_8), Map.class).get("token").toString();
    }

    static class MutableClock extends Clock {
        private Instant value = Instant.now();
        void advance(Duration duration) { value = value.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
        @Override public Instant instant() { return value; }
    }
}
