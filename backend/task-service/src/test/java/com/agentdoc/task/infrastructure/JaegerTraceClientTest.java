package com.agentdoc.task.infrastructure;

import com.agentdoc.task.config.TaskTraceProperties;
import com.agentdoc.task.constant.TaskTraceConstant;
import com.agentdoc.task.enums.TaskTraceAvailability;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用测试内临时 loopback HTTP 服务，不启动/停止项目服务或观测栈。 */
class JaegerTraceClientTest {
    private HttpServer server;
    private TaskTraceProperties properties;
    private JaegerTraceClient client;
    private final AtomicReference<byte[]> body = new AtomicReference<>("{\"data\":[]}".getBytes(StandardCharsets.UTF_8));
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicBoolean stallBody = new AtomicBoolean();
    private final CountDownLatch releaseBody = new CountDownLatch(1);

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/traces/", request -> {
            requests.incrementAndGet();
            authorization.set(request.getRequestHeaders().getFirst("Authorization"));
            if (status.get() == 302) {
                request.getResponseHeaders().add("Location", "/must-not-follow");
            }
            byte[] bytes = body.get();
            request.sendResponseHeaders(status.get(), bytes.length);
            try (var output = request.getResponseBody()) {
                if (stallBody.get()) {
                    output.write(bytes[0]); output.flush();
                    try { releaseBody.await(20, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    output.write(bytes, 1, bytes.length - 1);
                } else { output.write(bytes); }
            }
        });
        server.createContext("/must-not-follow", request -> {
            requests.incrementAndGet();
            request.sendResponseHeaders(500, -1); request.close();
        });
        server.start();
        properties = new TaskTraceProperties();
        properties.setQueryUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.validate();
        client = new JaegerTraceClient(properties);
    }

    @AfterEach
    void close() { releaseBody.countDown(); server.stop(0); }

    @Test
    void queriesFixedPathWithoutAuthorizationAndParsesResponse() {
        var result = client.fetch("a".repeat(32));
        assertThat(result.code()).isEqualTo(TaskTraceAvailability.AVAILABLE);
        assertThat(result.payload().path("data").isArray()).isTrue();
        assertThat(authorization.get()).isNull();
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void absentConfigInvalidTraceIdAndAllZeroNeverConnect() {
        assertThat(client.fetch("../../private").code()).isEqualTo(TaskTraceAvailability.INVALID_TRACE_ID);
        assertThat(client.fetch("0".repeat(32)).code()).isEqualTo(TaskTraceAvailability.INVALID_TRACE_ID);
        properties.setQueryUrl("");
        assertThat(client.fetch("a".repeat(32)).code()).isEqualTo(TaskTraceAvailability.NOT_CONFIGURED);
        assertThat(requests.get()).isZero();
    }

    @Test
    void oversizedOrMalformedBodiesReturnSafeCodes() {
        body.set(new byte[TaskTraceConstant.MAX_PAYLOAD_BYTES + 1]);
        assertThat(client.fetch("a".repeat(32)).code()).isEqualTo(TaskTraceAvailability.PAYLOAD_TOO_LARGE);
        body.set("{bad".getBytes(StandardCharsets.UTF_8));
        assertThat(client.fetch("a".repeat(32)).code()).isEqualTo(TaskTraceAvailability.PAYLOAD_INVALID);
    }

    @Test
    void redirectsAndBackendFailuresNeverExposeBodyOrFollowLocation() {
        status.set(302);
        assertThat(client.fetch("a".repeat(32)).code()).isEqualTo(TaskTraceAvailability.BACKEND_UNAVAILABLE);
        assertThat(requests.get()).isEqualTo(1);
        status.set(503);
        assertThat(client.fetch("a".repeat(32)).payload()).isNull();
        status.set(404);
        assertThat(client.fetch("a".repeat(32)).code()).isEqualTo(TaskTraceAvailability.NOT_FOUND_OR_NOT_SAMPLED);
    }

    @Test
    void configRejectsEmbeddedCredentialsQueriesFragmentsAndNonHttpSchemes() {
        for (String endpoint : new String[]{"http://user:secret@localhost", "http://localhost?token=x", "file:///secret", "http://localhost#fragment"}) {
            properties.setQueryUrl(endpoint);
            assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @Timeout(12)
    void responseBodyStallIsBoundedByReadTimeout() {
        stallBody.set(true);
        long start = System.nanoTime();
        assertThat(client.fetch("a".repeat(32)).code()).isEqualTo(TaskTraceAvailability.BACKEND_UNAVAILABLE);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(8000);
    }
}
