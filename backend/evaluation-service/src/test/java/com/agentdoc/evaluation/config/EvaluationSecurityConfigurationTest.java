package com.agentdoc.evaluation.config;

import com.agentdoc.common.config.CommonSecurityAutoConfiguration;
import com.agentdoc.common.config.CommonWebAutoConfiguration;
import com.agentdoc.evaluation.controller.EvaluationCatalogController;
import com.agentdoc.evaluation.service.EvaluationCatalogService;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import com.agentdoc.common.api.Result;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 验证 Evaluation 配置触发真实 Resource Server 过滤链，不启动业务服务。 */
@WebMvcTest(EvaluationCatalogController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ContextConfiguration(classes = EvaluationSecurityConfigurationTest.TestApplication.class)
class EvaluationSecurityConfigurationTest {

    private static RSAKey key;
    private static HttpServer jwks;

    @DynamicPropertySource
    static void jwksAddress(DynamicPropertyRegistry registry) throws Exception {
        key = new RSAKeyGenerator(2048).keyID("local-identity-test").generate();
        jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwks.createContext("/jwks", exchange -> {
            byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        jwks.start();
        registry.add("AUTH_IDENTITY_JWKS_URL", () -> "http://127.0.0.1:" + jwks.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwks() {
        if (jwks != null) { jwks.stop(0); }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({EvaluationCatalogController.class, CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
    static class TestApplication { }

    @Autowired private MockMvc mvc;
    @Autowired private JwtDecoder decoder;
    @MockitoBean private EvaluationCatalogService catalogService;
    @MockitoBean private AuthIdentityFeign identity;

    @Test
    void missingBearerDoesNotReachController() throws Exception {
        mvc.perform(post("/api/evaluation/datasets/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceId\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
        verifyNoInteractions(catalogService);
    }

    @Test
    void malformedBearerIsRejectedBeforeController() throws Exception {
        assertThat(decoder).isNotNull();
        mvc.perform(post("/api/evaluation/datasets/search")
                        .header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceId\":7}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(catalogService);
    }

    @Test
    void validSignedUserBearerReachesController() throws Exception {
        when(identity.currentIdentity(anyString())).thenReturn(Result.ok(new IdentitySelfVO(7L, List.of(), 0L)));
        var claims = new JWTClaimsSet.Builder().subject("7").issuer("urn:agent-doc-workbench:local-identity").audience(List.of("workbench-api"))
                .claim("scope", "workbench:access").claim("purpose", "HUMAN_ACCESS").claim("actorType", "HUMAN")
                .claim("azp", "workbench-web").claim("auth_version", 0L).claim("sid", UUID.randomUUID().toString()).issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(60))).build();
        var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("adw-human+jwt")).keyID(key.getKeyID()).build(), claims);
        token.sign(new RSASSASigner(key));
        when(catalogService.searchDatasets(any())).thenReturn(new PageVO<>(List.of(), 0, 1, 10));

        mvc.perform(post("/api/evaluation/datasets/search")
                        .header("Authorization", "Bearer " + token.serialize())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceId\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(catalogService).searchDatasets(any());
    }
}
