package com.agentdoc.agent.config;

import com.agentdoc.agent.controller.InternalAgentController;
import com.agentdoc.agent.service.AgentService;
import com.agentdoc.common.config.CommonSecurityAutoConfiguration;
import com.agentdoc.common.config.CommonWebAutoConfiguration;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P0执行档案最小权限回归（原匿名可达探针）；不依赖真实业务资源。 */
@WebMvcTest(InternalAgentController.class)
@ContextConfiguration(classes = {InternalAgentController.class, CommonSecurityAutoConfiguration.class,
        CommonWebAutoConfiguration.class, InternalAgentSecurityConfig.class})
class P0InternalEndpointBaselineTest {

    private static final String SERVICE_SECRET = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32));

    @DynamicPropertySource
    static void configureInternalCredential(DynamicPropertyRegistry registry) {
        registry.add("agent-doc.task-internal-auth.secret", () -> SERVICE_SECRET);
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private AgentService agentService;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void rejectsAnonymousOrUntrustedIdentityBeforeReadingProfile() throws Exception {
        mvc.perform(get("/api/agent/internal/agents/9001/execution-profile"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(URI.create("/api/%61gent/internal/agents/9001/execution-profile")))
                .andExpect(status().isUnauthorized());
        when(decoder.decode("user-token")).thenReturn(Jwt.withTokenValue("user-token")
                .header("alg", "RS256").subject("9901").claim("scope", "user").build());
        mvc.perform(get("/api/agent/internal/agents/9001/execution-profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/agent/internal/agents/9001/execution-profile")
                        .header(HttpHeaders.AUTHORIZATION, basic("task-service", SERVICE_SECRET + "invalid")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/agent/internal/agents/9001/execution-profile")
                        .header(HttpHeaders.AUTHORIZATION, basic("other-service", SERVICE_SECRET)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(agentService);
    }

    @Test
    void dedicatedTaskServiceCanReadTheProfile() throws Exception {
        when(agentService.executionProfile(9001L)).thenReturn(new AgentExecutionProfileVO(
                9001L, 9002L, 9003L, 100L, "[9004]", 1L, true,
                BigDecimal.ONE, BigDecimal.TEN));

        mvc.perform(get("/api/agent/internal/agents/9001/execution-profile").secure(true)
                        .header(HttpHeaders.AUTHORIZATION, basic("task-service", SERVICE_SECRET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.documentScope").value("[9004]"))
                .andExpect(jsonPath("$.data.tokenBudget").value(100))
                .andExpect(jsonPath("$.data.inputPricePerMillion").value(1))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
        verify(agentService).executionProfile(9001L);
    }

    private String basic(String client, String secret) {
        return "Basic " + HttpHeaders.encodeBasicAuth(client, secret, StandardCharsets.UTF_8);
    }
}
