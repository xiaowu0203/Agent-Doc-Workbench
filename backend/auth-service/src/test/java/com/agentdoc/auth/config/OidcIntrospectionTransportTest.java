package com.agentdoc.auth.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.agentdoc.auth.service.JwtService;
import com.agentdoc.common.feign.http.AuthenticationHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** 使用正式SecurityConfig和真实HTTP验证introspection预算及脱敏，不访问外部IdP。 */
class OidcIntrospectionTransportTest {
    private static final String TOKEN = "fixture-sensitive";
    private static final String SECRET = "introspection-test-secret";
    private HttpServer server;
    private ExecutorService handlers;
    private AtomicInteger requests;
    private AtomicInteger responseStatus;
    private AtomicInteger redirected;
    private volatile CountDownLatch hold;
    private volatile CountDownLatch entered;
    private final SecurityConfig config = new SecurityConfig(mock(JwtService.class));

    @BeforeEach
    void start() throws IOException {
        requests = new AtomicInteger(); responseStatus = new AtomicInteger(200); redirected = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        handlers = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(handlers);
        server.createContext("/introspect", exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo(
                    "Basic " + Base64.getEncoder().encodeToString(("workbench-api:" + SECRET).getBytes(StandardCharsets.UTF_8)));
            assertThat(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("token=" + TOKEN);
            if (entered != null) { entered.countDown(); }
            if (hold != null) {
                try { hold.await(6, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
            String json = "{\"active\":true,\"sub\":\"fixture-subject\",\"scope\":\"workbench:access\",\"exp\":"
                    + Instant.now().plusSeconds(300).getEpochSecond() + "}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (responseStatus.get() == 302) { exchange.getResponseHeaders().set("Location", origin() + "/steal"); }
            try { exchange.sendResponseHeaders(responseStatus.get(), body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.createContext("/steal", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
    }

    @AfterEach
    void stop() {
        if (hold != null) { hold.countDown(); }
        server.stop(0); handlers.shutdownNow();
    }

    @Test
    void realAdapterKeepsBasicAndTokenInRequestAndRedactsDebugLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(RestTemplate.class);
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> events = new ListAppender<>(); events.start(); logger.addAppender(events); logger.setLevel(Level.DEBUG);
        try (var transport = config.oidcIntrospectionTransport()) {
            assertThat(introspector(transport).introspect(TOKEN).getName()).isEqualTo("fixture-subject");
            assertThat(events.list).isNotEmpty();
            assertThat(events.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
                    .allSatisfy(message -> assertThat(message).doesNotContain(TOKEN, SECRET,
                            Base64.getEncoder().encodeToString(("workbench-api:" + SECRET).getBytes(StandardCharsets.UTF_8))));
        } finally { logger.setLevel(previous); logger.detachAppender(events); events.stop(); }
    }

    @Test
    void serviceErrorsAreNotRetriedAndRedirectsNeverReachAnotherEndpoint() {
        try (var transport = config.oidcIntrospectionTransport()) {
            OpaqueTokenIntrospector introspector = introspector(transport);
            responseStatus.set(503);
            assertThatThrownBy(() -> introspector.introspect(TOKEN)).isInstanceOf(OAuth2IntrospectionException.class);
            assertThat(requests).hasValue(1);
            responseStatus.set(302);
            assertThatThrownBy(() -> introspector.introspect(TOKEN)).isInstanceOf(OAuth2IntrospectionException.class);
            assertThat(requests).hasValue(2); assertThat(redirected).hasValue(0);
        }
    }

    @Test
    void actualIdpAdapterTimesOutReadingWithinItsTwoSecondBudget() {
        try (var transport = config.oidcIntrospectionTransport()) {
            OpaqueTokenIntrospector introspector = introspector(transport);
            introspector.introspect(TOKEN); hold = new CountDownLatch(1);
            long started = System.nanoTime();
            assertThatThrownBy(() -> introspector.introspect(TOKEN)).isInstanceOf(OAuth2IntrospectionException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(Duration.ofMillis(1500), Duration.ofMillis(3200));
            assertThat(requests).hasValue(2);
        }
    }

    @Test
    void seventeenthIdpRequestIsRejectedWithoutQueueingOrSendingAnotherToken() throws Exception {
        try (var transport = config.oidcIntrospectionTransport(); var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            OpaqueTokenIntrospector introspector = introspector(transport);
            introspector.introspect(TOKEN); hold = new CountDownLatch(1); entered = new CountDownLatch(16);
            var futures = new ArrayList<Future<?>>();
            for (int index = 0; index < 16; index++) { futures.add(callers.submit(() -> introspector.introspect(TOKEN))); }
            try {
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                long started = System.nanoTime();
                assertThatThrownBy(() -> introspector.introspect(TOKEN)).isInstanceOf(OAuth2IntrospectionException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(100));
                assertThat(requests).hasValue(17);
            } finally { hold.countDown(); }
            for (var future : futures) { future.get(2, TimeUnit.SECONDS); }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://secret:fixture@idp.invalid/introspect", "https://idp.invalid/introspect?secret=fixture",
            "https://idp.invalid/introspect#fixture", "ftp://idp.invalid/introspect", "http://bad uri/fixture"})
    void invalidOrSecretBearingEndpointFailsBeforeAnyRequestWithoutEchoingItsValue(String endpoint) {
        IdentityBindingProperties properties = new IdentityBindingProperties();
        properties.setIntrospectionUri(endpoint); properties.setIntrospectionClientSecret(SECRET);
        try (var transport = config.oidcIntrospectionTransport()) {
            assertThatThrownBy(() -> config.oidcTokenIntrospector(properties, transport))
                    .isInstanceOf(IllegalStateException.class).hasNoCause()
                    .hasMessageNotContaining(endpoint).hasMessageNotContaining("fixture");
            assertThat(requests).hasValue(0);
        }
    }

    private String origin() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    private OpaqueTokenIntrospector introspector(AuthenticationHttpClient transport) {
        IdentityBindingProperties properties = new IdentityBindingProperties();
        properties.setIntrospectionUri(origin() + "/introspect"); properties.setIntrospectionClientSecret(SECRET);
        return config.oidcTokenIntrospector(properties, transport);
    }
}
