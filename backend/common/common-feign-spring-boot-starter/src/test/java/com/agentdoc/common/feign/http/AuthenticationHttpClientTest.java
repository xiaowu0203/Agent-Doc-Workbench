package com.agentdoc.common.feign.http;

import com.agentdoc.common.feign.config.AuthenticationHttpBudget;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.DnsResolver;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

class AuthenticationHttpClientTest {
    private HttpServer server;
    private ExecutorService handlers;
    private static final Map<String, List<String>> HEADERS = Map.of("Authorization", List.of("Bearer fixture-sensitive"));

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        handlers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(handlers);
        server.createContext("/ok", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); handlers.shutdownNow(); }

    @Test
    void bodyAndAuthorizationAreTransferredAndHttp503IsNeverRetried() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/unavailable", exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture-sensitive");
            assertThat(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("token=fixture");
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        try (var client = client(200, 500, 1000, 2)) {
            assertThat(client.execute(uri("/unavailable"), "POST", HEADERS, "token=fixture".getBytes(StandardCharsets.UTF_8)).status()).isEqualTo(503);
            assertThat(requests).hasValue(1);
            assertReleased(client);
        }
    }

    @Test
    void connectionDroppedBeforeHeadersDoesNotRetryOrExposeSecrets() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/drop", exchange -> { requests.incrementAndGet(); exchange.close(); });
        try (var client = client(200, 500, 1000, 2)) {
            assertThatThrownBy(() -> client.execute(uri("/drop?secret=fixture"), "GET", HEADERS, null))
                    .isInstanceOf(IOException.class).hasMessage("认证依赖调用失败").hasNoCause();
            assertThat(requests).hasValue(1);
            assertReleased(client);
        }
    }

    @Test
    void redirectDoesNotForwardAuthorizationOrReachItsTarget() throws Exception {
        AtomicInteger target = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", uri("/steal").toString());
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/steal", exchange -> { target.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        try (var client = client(200, 500, 1000, 2)) {
            assertThat(client.execute(uri("/redirect"), "GET", HEADERS, null).status()).isEqualTo(302);
            assertThat(target).hasValue(0);
        }
    }

    @Test
    void absentHeadersHitReadTimeoutAndConnectionsRecover() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/hang", exchange -> {
            try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        try (var client = client(100, 200, 800, 1)) {
            long started = System.nanoTime();
            assertThatThrownBy(() -> client.execute(uri("/hang"), "GET", HEADERS, null)).isInstanceOf(IOException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(700));
            assertReleased(client);
            assertThat(client.execute(uri("/ok"), "GET", HEADERS, null).status()).isEqualTo(200);
        } finally { release.countDown(); }
    }

    @Test
    void bodyStallingAfterHeadersIsAlsoSubjectToReadTimeout() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/partial", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write('x'); exchange.getResponseBody().flush();
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        try (var client = client(100, 200, 800, 1)) {
            long started = System.nanoTime();
            assertThatThrownBy(() -> client.execute(uri("/partial"), "GET", HEADERS, null)).isInstanceOf(IOException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(700));
            assertReleased(client);
        } finally { release.countDown(); }
    }

    @Test
    void interruptedCallerCancelsItsRequestAndRetainsItsInterruptFlag() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        server.createContext("/interrupt", exchange -> {
            entered.countDown();
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        try (var client = client(200, 3000, 4000, 1)) {
            Thread caller = Thread.ofVirtual().start(() -> {
                try { client.execute(uri("/interrupt"), "GET", HEADERS, null); }
                catch (IOException error) { interrupted.set(Thread.currentThread().isInterrupted()); }
                finally { finished.countDown(); }
            });
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue(); caller.interrupt();
            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue(); assertThat(interrupted).isTrue(); assertReleased(client);
        } finally { release.countDown(); }
    }

    @Test
    void slowlyDrippingBodyCannotExtendTheTotalDeadline() throws Exception {
        server.createContext("/drip", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 100);
                for (int index = 0; index < 100; index++) {
                    exchange.getResponseBody().write('x'); exchange.getResponseBody().flush();
                    Thread.sleep(50);
                }
            } catch (IOException ignored) { /* 客户端取消应关闭连接。 */ }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        try (var client = client(100, 200, 500, 1)) {
            long started = System.nanoTime();
            assertThatThrownBy(() -> client.execute(uri("/drip"), "GET", HEADERS, null))
                    .isInstanceOf(IOException.class).hasMessage("认证依赖调用超时");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(Duration.ofMillis(400), Duration.ofSeconds(1));
            assertReleased(client);
            assertThat(client.execute(uri("/ok"), "GET", HEADERS, null).status()).isEqualTo(200);
        }
    }

    @Test
    void exhaustedAdmissionRejectsWithoutQueueingAnotherRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/hold", exchange -> {
            entered.countDown();
            try { release.await(3, TimeUnit.SECONDS); exchange.sendResponseHeaders(200, -1); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        try (var client = client(200, 3000, 4000, 1)) {
            assertThat(client.execute(uri("/ok"), "GET", HEADERS, null).status()).isEqualTo(200);
            var first = CompletableFuture.runAsync(() -> {
                try { client.execute(uri("/hold"), "GET", HEADERS, null); } catch (IOException error) { throw new RuntimeException(error); }
            });
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            long started = System.nanoTime();
            assertThatThrownBy(() -> client.execute(uri("/ok"), "GET", HEADERS, null)).isInstanceOf(IOException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(100));
            assertThat(client.inFlight()).isEqualTo(1);
            release.countDown(); first.get(2, TimeUnit.SECONDS); assertReleased(client);
        } finally { release.countDown(); }
    }

    @Test
    void totalDeadlineAlsoCoversDnsAndDoesNotReleaseAdmissionBeforeWorkEnds() throws Exception {
        DnsResolver resolver = mock(DnsResolver.class, CALLS_REAL_METHODS);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger resolutions = new AtomicInteger();
        AtomicInteger lateRequests = new AtomicInteger();
        server.createContext("/dns-proof", exchange -> {
            lateRequests.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        when(resolver.resolve(anyString())).thenAnswer(invocation -> {
            resolutions.incrementAndGet(); entered.countDown();
            boolean finished = false;
            while (!finished) {
                try { finished = release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { /* 模拟不能被Java中断终止的有限DNS调用。 */ }
            }
            return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
        });
        var budget = new AuthenticationHttpBudget(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(500), 1);
        try (var client = new AuthenticationHttpClient("test", budget, resolver)) {
            long started = System.nanoTime();
            assertThatThrownBy(() -> client.execute(URI.create("http://auth-test.invalid:" + server.getAddress().getPort() + "/dns-proof"), "GET", HEADERS, null))
                    .isInstanceOf(IOException.class).hasMessage("认证依赖调用超时");
            assertThat(entered.getCount()).isZero();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
            assertThat(client.inFlight()).isEqualTo(1);
            assertThatThrownBy(() -> client.execute(uri("/ok"), "GET", HEADERS, null)).isInstanceOf(IOException.class);
            assertThat(resolutions).hasValue(1);
            release.countDown(); assertReleased(client);
            assertThat(lateRequests).hasValue(0);
        } finally { release.countDown(); }
    }

    @Test
    void boundedWorkerPreservesTheRequestTelemetryContext() throws Exception {
        ContextKey<String> key = ContextKey.named("p1-http-test-correlation");
        AtomicReference<String> observed = new AtomicReference<>();
        DnsResolver resolver = mock(DnsResolver.class, CALLS_REAL_METHODS);
        when(resolver.resolve(anyString())).thenAnswer(invocation -> {
            observed.set(Context.current().get(key));
            return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
        });
        try (var client = new AuthenticationHttpClient("test", AuthenticationHttpBudget.IDENTITY, resolver);
             var scope = Context.current().with(key, "request-correlation").makeCurrent()) {
            assertThat(client.execute(URI.create("http://auth-context.invalid:" + server.getAddress().getPort() + "/ok"), "GET", HEADERS, null).status()).isEqualTo(200);
            assertThat(observed).hasValue("request-correlation");
        }
        assertThat(Context.current().get(key)).isNull();
    }

    private AuthenticationHttpClient client(int connect, int read, int total, int max) {
        return new AuthenticationHttpClient("test", new AuthenticationHttpBudget(
                Duration.ofMillis(connect), Duration.ofMillis(read), Duration.ofMillis(total), max));
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path); }
    private void assertReleased(AuthenticationHttpClient client) {
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(client.inFlight()).isZero(); assertThat(client.leasedConnections()).isZero();
        });
    }
}
