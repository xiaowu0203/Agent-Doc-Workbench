package com.agentdoc.common.feign.config;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import com.agentdoc.common.utils.JsonUtils;
import com.sun.net.httpserver.HttpServer;
import feign.Client;
import feign.Logger;
import feign.Response;
import feign.Retryer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 真实Spring命名Feign装配；全局Client/Retryer/Logger及属性不得覆盖本人身份边界。 */
class IdentitySelfFeignBoundaryTest {
    private HttpServer server;
    private ExecutorService handlers;
    private AtomicInteger requests;
    private AtomicInteger responseStatus;
    private AtomicInteger stolen;
    private volatile CountDownLatch hold;
    private volatile CountDownLatch entered;

    @BeforeEach
    void start() throws IOException {
        requests = new AtomicInteger(); responseStatus = new AtomicInteger(200); stolen = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        handlers = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(handlers);
        server.createContext("/api/auth/identity", exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture-sensitive");
            if (entered != null) { entered.countDown(); }
            if (hold != null) {
                try { hold.await(8, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
            byte[] body = JsonUtils.toJson(Result.ok(new IdentitySelfVO(42L, List.of(), 2L))).getBytes(StandardCharsets.UTF_8);
            if (responseStatus.get() == 302) { exchange.getResponseHeaders().set("Location", origin() + "/steal"); }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            try { exchange.sendResponseHeaders(responseStatus.get(), body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.createContext("/steal", exchange -> { stolen.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/api/auth/internal/platform-role", exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture-sensitive");
            assertThat(exchange.getRequestHeaders().getFirst("X-Task-Capability")).isNull();
            assertThat(exchange.getRequestURI().getQuery()).isEqualTo("roleKey=PLATFORM_SUPER_ADMIN");
            if (responseStatus.get() == 302) { exchange.getResponseHeaders().set("Location", origin() + "/steal"); }
            byte[] body = JsonUtils.toJson(Result.ok()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            try { exchange.sendResponseHeaders(responseStatus.get(), body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        if (hold != null) { hold.countDown(); }
        server.stop(0); handlers.shutdownNow();
    }

    @Test
    void platformRoleUsesSameDirectBoundedClientAndKeeps401403503Distinct() {
        runner().run(context -> {
            var identity = context.getBean(AuthIdentityFeign.class);
            assertThat(identity.checkPlatformRole("Bearer fixture-sensitive", "PLATFORM_SUPER_ADMIN").code()).isZero();
            responseStatus.set(401);
            assertThatThrownBy(() -> identity.checkPlatformRole("Bearer fixture-sensitive", "PLATFORM_SUPER_ADMIN"))
                    .isInstanceOf(BadCredentialsException.class);
            responseStatus.set(403);
            assertThatThrownBy(() -> identity.checkPlatformRole("Bearer fixture-sensitive", "PLATFORM_SUPER_ADMIN"))
                    .isInstanceOf(AccessDeniedException.class);
            for (int status : new int[]{503, 302}) {
                responseStatus.set(status);
                assertThatThrownBy(() -> identity.checkPlatformRole("Bearer fixture-sensitive", "PLATFORM_SUPER_ADMIN"))
                        .isInstanceOf(AuthenticationServiceException.class);
            }
            assertThat(requests).hasValue(5);
            assertThat(stolen).hasValue(0);
            assertThat(context.getBean(GeneralOverrides.class).logged).hasValue(0);
            assertThat(context.getBean(GeneralOverrides.class).ordinaryCalls).hasValue(0);
        });
    }

    @Test
    void factoryLocksItsOwnClientRetryerLoggerAndRedirectPolicyWithoutReplacingOtherClients() {
        runner().run(context -> {
            AuthIdentityFeign identity = context.getBean(AuthIdentityFeign.class);
            var general = context.getBean(GeneralOverrides.class);
            assertThat(identity.currentIdentity("Bearer fixture-sensitive").data().authVersion()).isEqualTo(2L);
            responseStatus.set(503);
            assertThatThrownBy(() -> identity.currentIdentity("Bearer fixture-sensitive")).isInstanceOf(AuthenticationServiceException.class);
            assertThat(requests).hasValue(2);
            responseStatus.set(302);
            assertThatThrownBy(() -> identity.currentIdentity("Bearer fixture-sensitive")).isInstanceOf(AuthenticationServiceException.class);
            assertThat(stolen).hasValue(0);
            assertThat(general.logged).hasValue(0);
            assertThat(general.ordinaryCalls).hasValue(0);
            assertThat(context.getBean(OrdinaryProbe.class).get()).isEqualTo("untouched");
            assertThat(general.ordinaryCalls).hasValue(1);
        });
    }

    @Test
    void actualSpringClientUsesFiveSecondReadTimeoutEvenWhenPropertiesAttemptToIncreaseIt() {
        hold = new CountDownLatch(1);
        runner().run(context -> {
            long started = System.nanoTime();
            assertThatThrownBy(() -> context.getBean(AuthIdentityFeign.class).currentIdentity("Bearer fixture-sensitive"))
                    .isInstanceOf(RuntimeException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(Duration.ofSeconds(4), Duration.ofMillis(6500));
            assertThat(requests).hasValue(1);
        });
    }

    @Test
    void thirtyThirdRequestFailsFastWithoutReachingAuth() {
        hold = new CountDownLatch(1); entered = new CountDownLatch(32);
        runner().run(context -> {
            AuthIdentityFeign identity = context.getBean(AuthIdentityFeign.class);
            try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new ArrayList<Future<Result<IdentitySelfVO>>>();
                for (int index = 0; index < 32; index++) {
                    futures.add(callers.submit(() -> identity.currentIdentity("Bearer fixture-sensitive")));
                }
                try {
                    assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                    long started = System.nanoTime();
                    assertThatThrownBy(() -> identity.currentIdentity("Bearer fixture-sensitive")).isInstanceOf(RuntimeException.class);
                    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(100));
                    assertThat(requests).hasValue(32);
                } finally { hold.countDown(); }
                for (var future : futures) { assertThat(future.get(2, TimeUnit.SECONDS).data().userId()).isEqualTo(42L); }
            }
        });
    }

    @Test
    void unsupportedLazyFactoryCannotSilentlyBypassTheBoundary() {
        runner().withPropertyValues("spring.cloud.openfeign.lazy-attributes-resolution=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("本人身份Feign边界要求关闭lazy-attributes-resolution");
        });
    }

    private String origin() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class,
                        org.springframework.cloud.openfeign.FeignAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class))
                .withUserConfiguration(GeneralOverrides.class)
                .withPropertyValues("agent-doc.feign.identity-auth-url=" + origin(),
                        "spring.cloud.openfeign.client.config.auth-identity-self.connectTimeout=30000",
                        "spring.cloud.openfeign.client.config.auth-identity-self.readTimeout=30000",
                        "spring.cloud.openfeign.client.config.auth-identity-self.followRedirects=true",
                        "spring.cloud.openfeign.client.config.auth-identity-self.loggerLevel=FULL");
    }

    @FeignClient(name = "p1-ordinary-probe", url = "http://127.0.0.1:1")
    interface OrdinaryProbe { @GetMapping("/probe") String get(); }

    @Configuration(proxyBeanMethods = false)
    static class GeneralOverrides {
        final AtomicInteger ordinaryCalls = new AtomicInteger();
        final AtomicInteger logged = new AtomicInteger();
        @Bean Client generalClient() {
            return (request, options) -> {
                ordinaryCalls.incrementAndGet();
                return Response.builder().status(200).request(request).headers(Map.of())
                        .body("untouched", StandardCharsets.UTF_8).build();
            };
        }
        @Bean Retryer generalRetryer() { return new Retryer.Default(1, 1, 3); }
        @Bean Logger generalLogger() {
            return new Logger() { @Override protected void log(String key, String format, Object... args) { logged.incrementAndGet(); } };
        }
    }
}
