package com.agentdoc.common.feign.http;

import com.agentdoc.common.feign.config.AuthenticationHttpBudget;
import com.agentdoc.common.feign.enums.AuthenticationHttpOutcome;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static com.agentdoc.common.feign.enums.AuthenticationHttpOutcome.COMPLETED;
import static com.agentdoc.common.feign.enums.AuthenticationHttpOutcome.FAILURE;
import static com.agentdoc.common.feign.enums.AuthenticationHttpOutcome.INTERRUPTED;
import static com.agentdoc.common.feign.enums.AuthenticationHttpOutcome.REJECTED;
import static com.agentdoc.common.feign.enums.AuthenticationHttpOutcome.TIMEOUT;

/** 两条认证依赖共用的有界HTTP传输；不记录URL、凭据、正文或底层异常消息。 */
@Slf4j
public final class AuthenticationHttpClient implements AutoCloseable {

    private static final LongCounter CALLS = GlobalOpenTelemetry.getMeter("agent-doc.authentication")
            .counterBuilder("auth.dependency.calls").build();
    private static final DoubleHistogram DURATION = GlobalOpenTelemetry.getMeter("agent-doc.authentication")
            .histogramBuilder("auth.dependency.duration").setUnit("s").build();
    private static final LongUpDownCounter IN_FLIGHT = GlobalOpenTelemetry.getMeter("agent-doc.authentication")
            .upDownCounterBuilder("auth.dependency.in_flight").build();
    private static final long CONNECTION_LEASE_MILLIS = 100;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final String dependency;
    private final AuthenticationHttpBudget budget;
    private final Attributes dependencyTags;
    private final PoolingHttpClientConnectionManager pool;
    private final CloseableHttpClient client;
    private final Semaphore permits;
    private final Map<HttpUriRequestBase, Thread> active = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public AuthenticationHttpClient(String dependency, AuthenticationHttpBudget budget) {
        this(dependency, budget, null);
    }

    AuthenticationHttpClient(String dependency, AuthenticationHttpBudget budget, DnsResolver resolver) {
        this.dependency = dependency;
        this.budget = budget;
        dependencyTags = Attributes.of(stringKey("dependency"), dependency);
        permits = new Semaphore(budget.maxInFlight());
        pool = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(resolver)
                .setMaxConnTotal(budget.maxInFlight()).setMaxConnPerRoute(budget.maxInFlight())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(budget.connectTimeout()))
                        .setSocketTimeout(Timeout.of(budget.readTimeout())).build())
                .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.of(budget.connectTimeout())).build())
                .build();
        client = HttpClients.custom().setConnectionManager(pool)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECTION_LEASE_MILLIS))
                        .setAuthenticationEnabled(false)
                        .setResponseTimeout(Timeout.of(budget.readTimeout())).build())
                .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().disableAuthCaching().build();
    }

    /** 整体期限覆盖请求发起、DNS及完整响应读取；许可由实际执行线程释放。 */
    public Response execute(URI uri, String method, Map<String, List<String>> headers, byte[] body) throws IOException {
        long started = System.nanoTime();
        AuthenticationHttpOutcome outcome = FAILURE;
        HttpUriRequestBase request = new HttpUriRequestBase(method, uri);
        headers.forEach((name, values) -> {
            if (!HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name) && !HttpHeaders.HOST.equalsIgnoreCase(name)
                    && !HttpHeaders.CONNECTION.equalsIgnoreCase(name)) {
                values.forEach(value -> request.addHeader(name, value));
            }
        });
        if (body != null && body.length > 0) {
            var contentType = request.getFirstHeader(HttpHeaders.CONTENT_TYPE);
            request.setEntity(new ByteArrayEntity(body, contentType == null ? ContentType.APPLICATION_OCTET_STREAM
                    : ContentType.parse(contentType.getValue())));
        }
        if (closed.get() || !permits.tryAcquire()) {
            record(REJECTED, started);
            throw new IOException("认证依赖暂不可用");
        }
        CompletableFuture<Response> result = new CompletableFuture<>();
        IN_FLIGHT.add(1, dependencyTags);
        Thread worker = Thread.ofVirtual().name("auth-http-" + dependency).unstarted(Context.current().wrap(() -> {
            Response response = null;
            Exception failure = null;
            try {
                if (closed.get() || request.isCancelled()) { throw new IOException("认证依赖调用已取消"); }
                response = client.execute(request, incoming -> {
                    HttpHeaders responseHeaders = new HttpHeaders();
                    for (var header : incoming.getHeaders()) { responseHeaders.add(header.getName(), header.getValue()); }
                    byte[] bytes = incoming.getEntity() == null ? new byte[0] : EntityUtils.toByteArray(incoming.getEntity());
                    return new Response(incoming.getCode(), incoming.getReasonPhrase(), HttpHeaders.readOnlyHttpHeaders(responseHeaders), bytes);
                });
            } catch (Exception error) {
                failure = error;
            } finally {
                active.remove(request);
                permits.release();
                IN_FLIGHT.add(-1, dependencyTags);
            }
            if (failure == null) { result.complete(response); } else { result.completeExceptionally(failure); }
        }));
        active.put(request, worker);
        worker.start();
        try {
            Response response = result.get(Math.max(1, budget.totalTimeout().toNanos() - (System.nanoTime() - started)), TimeUnit.NANOSECONDS);
            outcome = COMPLETED;
            return response;
        } catch (TimeoutException error) {
            outcome = TIMEOUT;
            throw new IOException("认证依赖调用超时");
        } catch (InterruptedException error) {
            outcome = INTERRUPTED;
            Thread.currentThread().interrupt();
            throw new IOException("认证依赖调用已中断");
        } catch (ExecutionException error) {
            if (error.getCause() instanceof InterruptedIOException || error.getCause() instanceof TimeoutException) {
                outcome = TIMEOUT;
            }
            throw new IOException("认证依赖调用失败");
        } finally {
            if (!result.isDone()) { request.cancel(); worker.interrupt(); }
            record(outcome, started);
        }
    }

    /** 供Auth的RestTemplate适配，响应已在期限内完整读取，不把连接交给上层持有。 */
    public ClientHttpRequestFactory requestFactory() {
        return (uri, method) -> new AbstractClientHttpRequest() {
            private final ByteArrayOutputStream body = new ByteArrayOutputStream();
            @Override public HttpMethod getMethod() { return method; }
            @Override public URI getURI() { return uri; }
            @Override protected OutputStream getBodyInternal(HttpHeaders headers) { return body; }
            @Override protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
                Response response = AuthenticationHttpClient.this.execute(uri, method.name(), headers, body.toByteArray());
                return new ClientHttpResponse() {
                    @Override public HttpStatusCode getStatusCode() { return HttpStatusCode.valueOf(response.status()); }
                    @Override public String getStatusText() { return response.reason() == null ? "" : response.reason(); }
                    @Override public HttpHeaders getHeaders() { return response.headers(); }
                    @Override public InputStream getBody() { return new ByteArrayInputStream(response.body()); }
                    @Override public void close() { /* 响应及连接已在执行线程内关闭。 */ }
                };
            }
        };
    }

    private void record(AuthenticationHttpOutcome outcome, long started) {
        Attributes tags = Attributes.of(stringKey("dependency"), dependency, stringKey("outcome"), outcome.metricValue());
        CALLS.add(1, tags);
        DURATION.record((System.nanoTime() - started) / NANOS_PER_SECOND, tags);
        if (outcome != COMPLETED) { log.warn("认证依赖调用未完成 dependency={} outcome={}", dependency, outcome.metricValue()); }
    }

    int inFlight() { return budget.maxInFlight() - permits.availablePermits(); }
    int leasedConnections() { return pool.getTotalStats().getLeased(); }

    @Override public void close() {
        closed.set(true);
        active.forEach((request, worker) -> { request.cancel(); worker.interrupt(); });
        client.close(CloseMode.IMMEDIATE);
    }

    /** @param status HTTP状态码 @param reason 状态说明 @param headers 响应头 @param body 完整响应体 */
    public record Response(int status, String reason, HttpHeaders headers, byte[] body) { }
}
