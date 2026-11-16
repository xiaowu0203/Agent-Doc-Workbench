package com.agentdoc.auth;

import com.agentdoc.common.utils.JsonUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** IdP状态检查失败时的真实安全链响应；不记录Bearer或客户端密钥。 */
@EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_OIDC_POC", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class OidcIntrospectionFailureSecurityTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();
    @Autowired private MockMvc mvc;
    private static HttpServer provider;
    private static ExecutorService handlers;
    private static final AtomicInteger providerStatus = new AtomicInteger(503);
    private static final AtomicInteger requests = new AtomicInteger();
    private static final CountDownLatch release = new CountDownLatch(1);

    @BeforeAll
    static void startProvider() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        handlers = Executors.newVirtualThreadPerTaskExecutor(); provider.setExecutor(handlers);
        provider.createContext("/introspect", exchange -> {
            requests.incrementAndGet();
            if (providerStatus.get() == 0) {
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
                return;
            }
            byte[] body = (providerStatus.get() == 200 ? "{\"active\":false}" : "{\"error\":\"fixture-provider-detail\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            try { exchange.sendResponseHeaders(providerStatus.get(), body.length); exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        provider.start();
    }

    @AfterAll
    static void stopProvider() { release.countDown(); provider.stop(0); handlers.shutdownNow(); }

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) throws Exception {
        var fixture = JsonUtils.parse(Files.readString(ROOT.resolve(".codex-validation-store/v0.3.0-p0/runtime/oidc-poc.json")), Map.class);
        registry.add("workbench.auth.mode", () -> "OIDC");
        registry.add("workbench.auth.oidc.issuer", () -> fixture.get("issuer"));
        registry.add("workbench.auth.oidc.introspection-uri", () -> "http://127.0.0.1:" + provider.getAddress().getPort() + "/introspect");
        registry.add("workbench.auth.oidc.introspection-client-secret", () -> "fixture-introspection-secret");
    }

    @Test
    void identityProviderFailureReturns503AndInactiveTokenReturns401() throws Exception {
        String python = "import sys,importlib.util;sys.dont_write_bytecode=True;"
                + "s=importlib.util.spec_from_file_location('p',sys.argv[1]);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);"
                + "m.KeycloakPocTest.setUpClass();p=m.KeycloakPocTest('test_authorization_code_cannot_be_reused');"
                + "c,v,_=p.authorization_code();r=p.exchange(c,v);assert r.status_code==200;sys.stdout.write(r.json()['access_token'])";
        Process process = new ProcessBuilder("python", "-c", python, ROOT.resolve("deploy/identity/Test-PocIdentity.py").toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String token;
        try {
            token = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); }
        }
        String response = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(token, "fixture-provider-detail", "fixture-introspection-secret");
        providerStatus.set(200);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        providerStatus.set(0);
        long started = System.nanoTime();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isServiceUnavailable());
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(3500));
        assertThat(requests).hasValue(3);
    }
}
