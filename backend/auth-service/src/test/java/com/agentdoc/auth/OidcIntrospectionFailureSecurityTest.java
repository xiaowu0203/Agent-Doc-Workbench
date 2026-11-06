package com.agentdoc.auth;

import com.agentdoc.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** IdP状态检查失败时的真实安全链响应；不记录Bearer或客户端密钥。 */
@EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_OIDC_POC", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class OidcIntrospectionFailureSecurityTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();
    @Autowired private MockMvc mvc;
    @MockitoBean private OpaqueTokenIntrospector introspector;

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry registry) throws Exception {
        var fixture = JsonUtils.parse(Files.readString(ROOT.resolve(".codex-validation-store/v0.3.0-p0/runtime/oidc-poc.json")), Map.class);
        registry.add("workbench.auth.mode", () -> "OIDC");
        registry.add("workbench.auth.oidc.issuer", () -> fixture.get("issuer"));
    }

    @Test
    void identityProviderFailureReturns503AndInactiveTokenReturns401() throws Exception {
        var fixture = JsonUtils.parse(Files.readString(ROOT.resolve(".codex-validation-store/v0.3.0-p0/runtime/oidc-poc.json")), Map.class);
        String secret = fixture.get("taskClientId") + ":" + fixture.get("taskClientSecret");
        var request = HttpRequest.newBuilder(URI.create(fixture.get("issuer") + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String token = JsonUtils.parse(response.body(), Map.class).get("access_token").toString();
        doThrow(new OAuth2IntrospectionException("fixture unavailable")).when(introspector).introspect(anyString());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isServiceUnavailable());
        doThrow(new BadOpaqueTokenException("inactive")).when(introspector).introspect(anyString());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
}
