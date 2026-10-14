package com.agentdoc.auth;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.service.JwtService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.OnlineDocumentFeign;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.OnlineTaskFeign;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.feign.vo.OnlineTaskDispatchProofVO;
import com.agentdoc.common.feign.vo.OnlineSlotPermitVO;
import com.agentdoc.common.constant.JwtConstant;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import com.agentdoc.common.feign.vo.OnlineAuthorizationProofVO;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;

/** 实际安全过滤链/Controller/签发器集成；RPC 模拟，H2 不建业务表，不调用模型。 */
@SpringBootTest(properties = {"agent-doc.security.online-capability-enabled=true", "agent-doc.security.online-capability-issuer=test"})
@AutoConfigureMockMvc
class OnlineCapabilitySecurityIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService signer;
    @MockitoBean private OnlineDocumentFeign onlineDocument;
    @MockitoBean private OnlineEvaluationFeign evaluation;
    @MockitoBean private OnlineTaskFeign tasks;

    @BeforeEach
    void setup() {
        var proof = new OnlineAuthorizationProofVO("11", "22", "a".repeat(64), 2, "CREATED", null);
        when(evaluation.humanProof("11")).thenReturn(Result.ok(proof));
        when(evaluation.controlProof(eq("11"), anyString())).thenReturn(Result.ok(new OnlineAuthorizationProofVO("11", "22", proof.manifestHash(), 2, "ACTIVE", "501")));
        when(onlineDocument.requireHumanProtectionPermission("22")).thenReturn(Result.ok());
        when(onlineDocument.requireProtectionPermission(eq("11"), anyString())).thenReturn(Result.ok());
    }

    @Test
    void signedWaitTraversesActualHttpSecurityAndOnlyExchangesOriginalPendingIdentity() throws Exception {
        var binding = new OnlineTaskBindingDTO("61", "11", "a".repeat(64), "1", "BASELINE", 9000,
                "501", "10", "22", "30", "40", "1", "b".repeat(64), 1, "c".repeat(64), "71", 2, "d".repeat(64),
                "e".repeat(64), "f".repeat(64), "100", 600, "LIVE", "ORIGINAL", 2);
        var request = new OnlineAssignmentRequestDTO("10", "22", "30", "40", "501", "original-key",
                "a".repeat(64), "c".repeat(64), "1", "b".repeat(64), "100", 1);
        var proof = new OnlineTaskDispatchProofVO(request, binding, "PENDING", List.of(JwtConstant.ACTION_READ_FRAGMENT), "f".repeat(64));
        when(tasks.humanDispatchProof("10")).thenReturn(Result.ok(proof));
        when(tasks.waitDispatchProof(eq("10"), anyString())).thenReturn(Result.ok(proof));
        when(evaluation.waiting(eq("10"), anyString())).thenReturn(Result.ok(binding));
        when(evaluation.permit(eq("10"), anyString())).thenReturn(Result.ok(new OnlineSlotPermitVO(binding,
                OnlineProtocolUtils.hash("online.binding", binding), 3, "9".repeat(64), false)));
        String body = mvc.perform(post("/api/auth/internal/online-waits/10/human").header("Authorization", "Bearer " + user()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        String wait = OnlineProtocolUtils.object(body).path("data").asText();
        mvc.perform(post("/api/auth/internal/online-waits/10/renew").header(OnlineCapabilityConstant.HEADER, wait))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        String exchanged = mvc.perform(post("/api/auth/internal/online-waits/10/exchange").header(OnlineCapabilityConstant.HEADER, wait))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        var jwt = NimbusJwtDecoder.withPublicKey(signer.getPublicKey()).build().decode(OnlineProtocolUtils.object(exchanged).path("data").asText());
        assertThat(jwt.getClaimAsString(OnlineCapabilityConstant.BINDING_HASH)).isEqualTo(OnlineProtocolUtils.hash("online.binding", binding));
        mvc.perform(post("/api/auth/internal/online-waits/10/exchange").header("Authorization", "Bearer " + wait))
                .andExpect(status().isUnauthorized());
        when(tasks.waitDispatchProof(eq("10"), anyString())).thenReturn(Result.ok(new OnlineTaskDispatchProofVO(request, binding, "TERMINATED", proof.actions(), proof.releaseHash())));
        mvc.perform(post("/api/auth/internal/online-waits/10/exchange").header(OnlineCapabilityConstant.HEADER, wait))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void realSignedControlRenewsOnlyThroughDedicatedHeaderAndCannotEnterGeneralAuth() throws Exception {
        String token = control();
        mvc.perform(post("/api/auth/internal/online-capabilities").header(OnlineCapabilityConstant.HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"ONLINE_CONTROL\",\"taskIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        for (String path : List.of("/api/auth/internal/task-capabilities", "/api/auth/internal/online-control-authorizations")) {
            mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40100));
        }
        mvc.perform(post("/api/auth/internal/online-capabilities").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mixedIdentityAndCleartextDoNotReachIssuanceController() throws Exception {
        String token = control();
        mvc.perform(post("/api/auth/internal/online-capabilities").header(OnlineCapabilityConstant.HEADER, token)
                .header("Authorization", "Bearer " + user()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/internal/online-capabilities").header(OnlineCapabilityConstant.HEADER, token)
                .header("X-Forwarded-Proto", "https").with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private String control() throws Exception {
        String result = mvc.perform(post("/api/auth/internal/online-control-authorizations").header("Authorization", "Bearer " + user())
                .contentType(MediaType.APPLICATION_JSON).content("{\"experimentId\":\"11\",\"manifestHash\":\"" + "a".repeat(64)
                        + "\",\"automaticCancellationAcknowledged\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        return OnlineProtocolUtils.object(result).path("data").asText();
    }

    @Test
    void httpProtocolRejectsNumericLongIdsAndBooleanCoercionBeforeAuthorityRpc() throws Exception {
        String token = control(); clearInvocations(evaluation, onlineDocument);
        for (String json : List.of("{\"experimentId\":9223372036854775807,\"manifestHash\":\"" + "a".repeat(64)
                        + "\",\"automaticCancellationAcknowledged\":true}",
                "{\"experimentId\":\"11\",\"manifestHash\":\"" + "a".repeat(64) + "\",\"automaticCancellationAcknowledged\":\"true\"}")) {
            mvc.perform(post("/api/auth/internal/online-control-authorizations").header("Authorization", "Bearer " + user())
                    .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40000));
        }
        mvc.perform(post("/api/auth/internal/online-capabilities").header(OnlineCapabilityConstant.HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"ONLINE_OBSERVE\",\"taskIds\":[9223372036854775807]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(evaluation, onlineDocument);
    }
    private String user() {
        var user = new UserEntity(); user.setId(501L); user.setUsername("owner");
        return signer.createAccessToken(user, List.of());
    }
}
