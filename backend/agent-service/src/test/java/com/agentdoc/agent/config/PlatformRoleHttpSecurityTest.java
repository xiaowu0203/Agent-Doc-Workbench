package com.agentdoc.agent.config;

import com.agentdoc.agent.controller.ModelController;
import com.agentdoc.agent.service.ModelService;
import com.agentdoc.agent.service.PlatformAccessService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.CommonSecurityAutoConfiguration;
import com.agentdoc.common.config.CommonWebAutoConfiguration;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import com.agentdoc.common.enums.ErrorCode;
import org.springframework.security.authentication.BadCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 平台角色复核必须穿过真实方法鉴权表达式，不能把依赖故障混为无权限。 */
@WebMvcTest(ModelController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ContextConfiguration(classes = {ModelController.class, PlatformAccessService.class,
        CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class PlatformRoleHttpSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ModelService modelService;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private AuthIdentityFeign identity;

    @Test
    void roleDependencyFailureReturns503ThroughActualPreAuthorize() throws Exception {
        when(identity.checkPlatformRole("Bearer verified-human-fixture", SUPER_ADMIN))
                .thenThrow(new IllegalStateException("private dependency detail"));
        human(List.of(SUPER_ADMIN));
        mvc.perform(get("/api/agent/models").header("Authorization", "Bearer verified-human-fixture"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(modelService);
    }

    @Test
    void revokedRoleAndInvalidIdentityKeepTheirStatusThroughExpressionWrapping() throws Exception {
        human(List.of(SUPER_ADMIN));
        when(identity.checkPlatformRole("Bearer verified-human-fixture", SUPER_ADMIN))
                .thenReturn(Result.fail(ErrorCode.FORBIDDEN));
        mvc.perform(get("/api/agent/models").header("Authorization", "Bearer verified-human-fixture"))
                .andExpect(status().isForbidden());
        when(identity.checkPlatformRole("Bearer verified-human-fixture", SUPER_ADMIN))
                .thenThrow(new BadCredentialsException("private identity detail"));
        mvc.perform(get("/api/agent/models").header("Authorization", "Bearer verified-human-fixture"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40100));
        verifyNoInteractions(modelService);
    }

    @Test
    void missingRoleReturns403AndConfirmedRoleMayRead() throws Exception {
        human(List.of());
        mvc.perform(get("/api/agent/models").header("Authorization", "Bearer verified-human-fixture"))
                .andExpect(status().isForbidden());
        when(identity.checkPlatformRole("Bearer verified-human-fixture", SUPER_ADMIN)).thenReturn(Result.ok());
        when(modelService.list(false)).thenReturn(List.of());
        human(List.of(SUPER_ADMIN));
        mvc.perform(get("/api/agent/models").header("Authorization", "Bearer verified-human-fixture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    private void human(List<String> roles) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("verified-human-fixture").header("alg", "RS256")
                .header("typ", "adw-human+jwt").header("kid", "local-identity-fixture")
                .claim("iss", "urn:agent-doc-workbench:local-identity").subject("9002")
                .audience(List.of("workbench-api")).issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("purpose", "HUMAN_ACCESS").claim("scope", "workbench:access")
                .claim("actorType", "HUMAN").claim("azp", "workbench-web").claim("auth_version", 0L)
                .claim("sid", UUID.randomUUID().toString()).build();
        when(decoder.decode("verified-human-fixture")).thenReturn(jwt);
        when(identity.currentIdentity("Bearer verified-human-fixture"))
                .thenReturn(Result.ok(new IdentitySelfVO(9002L, roles, 0L)));
    }
}
