package com.agentdoc.auth;

import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.service.JwtService;
import com.agentdoc.auth.support.LegacyHumanCredentialFixture;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.TaskCapabilityIssueDTO;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0最小止损回归（原缺口探针）：普通用户不能调用内部签发，独立机器认证可用。
 * 使用运行时随机测试凭据、临时签名密钥和不存在的资源ID，不调用外部服务。
 * 权威业务证明和actions推导仍在P3验证，机器认证成功不代表业务授权完整。
 */
@SpringBootTest
@AutoConfigureMockMvc
class P0CapabilityIssuanceBaselineTest {

    private static final String SNAPSHOT_HASH = "a".repeat(64);
    private static final String UNAUTHORIZED_ACTION = "P0_UNAUTHORIZED_ACTION";
    private static final String SERVICE_SECRET = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32));

    @DynamicPropertySource
    static void configureInternalCredential(DynamicPropertyRegistry registry) {
        registry.add("agent-doc.task-internal-auth.secret", () -> SERVICE_SECRET);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;

    @Test
    void rejectsUserAccessToTaskSigning() throws Exception {
        TaskCapabilityIssueDTO request = new TaskCapabilityIssueDTO(9001L, 9002L, 9003L, 9004L,
                TaskExecutionMode.LIVE.name(), 0L, SNAPSHOT_HASH, 1, SNAPSHOT_HASH,
                null, List.of(UNAUTHORIZED_ACTION), null);

        mvc.perform(post("/api/auth/internal/task-capabilities")
                        .header("Authorization", "Bearer " + lowPrivilegeAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void encodedDirectPathCannotUseAUserTokenToBypassMachineAuthentication() throws Exception {
        TaskCapabilityIssueDTO request = new TaskCapabilityIssueDTO(9001L, 9002L, 9003L, 9004L,
                TaskExecutionMode.LIVE.name(), 0L, SNAPSHOT_HASH, 1, SNAPSHOT_HASH,
                null, List.of(UNAUTHORIZED_ACTION), null);
        mvc.perform(post(URI.create("/api/%61uth/internal/task-capabilities"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + lowPrivilegeAccessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJson(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUserAccessToWorkerSigning() throws Exception {
        EvaluationWorkerCapabilityIssueDTO request = new EvaluationWorkerCapabilityIssueDTO(
                9101L, 9102L, SNAPSHOT_HASH, 300L, List.of(UNAUTHORIZED_ACTION));

        mvc.perform(post("/api/auth/internal/evaluation-worker-capabilities")
                        .header("Authorization", "Bearer " + lowPrivilegeAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonUtils.toJson(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void acceptsDedicatedTaskIdentityForBothExistingSigningContracts() throws Exception {
        TaskCapabilityIssueDTO task = new TaskCapabilityIssueDTO(9001L, 9002L, 9003L, 9004L,
                TaskExecutionMode.LIVE.name(), 0L, SNAPSHOT_HASH, 1, SNAPSHOT_HASH,
                null, List.of(JwtConstant.ACTION_READ_FRAGMENT), null);
        EvaluationWorkerCapabilityIssueDTO worker = new EvaluationWorkerCapabilityIssueDTO(
                9101L, 9102L, SNAPSHOT_HASH, 300L, List.of("QUERY"));
        String taskResponse = sign("/api/auth/internal/task-capabilities", task);
        String workerResponse = sign("/api/auth/internal/evaluation-worker-capabilities", worker);
        assertThat(decodeResponse(taskResponse).getAudience()).containsExactly(JwtConstant.TASK_CAPABILITY_AUDIENCE);
        assertThat(decodeResponse(workerResponse).getAudience()).containsExactly(JwtConstant.EVALUATION_WORKER_CAPABILITY_AUDIENCE);
    }

    @Test
    void rejectsMissingWrongAndMixedCredentialsWithoutOpeningUserApiToBasic() throws Exception {
        for (String path : List.of("/api/auth/internal/task-capabilities", "/api/auth/internal/evaluation-worker-capabilities")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, basic("other-service", SERVICE_SECRET))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, basic("task-service", SERVICE_SECRET + "invalid"))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION,
                            basic("task-service", SERVICE_SECRET), "Bearer " + lowPrivilegeAccessToken())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, basic("task-service", SERVICE_SECRET)))
                .andExpect(status().isUnauthorized());
    }

    private String sign(String path, Object request) throws Exception {
        return mvc.perform(post(path).secure(true)
                        .header(HttpHeaders.AUTHORIZATION, basic("task-service", SERVICE_SECRET))
                        .contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJson(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
    }

    private String basic(String client, String secret) {
        return "Basic " + HttpHeaders.encodeBasicAuth(client, secret, StandardCharsets.UTF_8);
    }

    private String lowPrivilegeAccessToken() {
        UserEntity user = new UserEntity();
        user.setId(9901L);
        user.setUsername("p0-no-platform-role");
        return LegacyHumanCredentialFixture.issue(jwtService, user);
    }

    private Jwt decodeResponse(String response) {
        String token = JsonUtils.parse(response, JsonNode.class)
                .path("data").asText();
        return NimbusJwtDecoder.withPublicKey(jwtService.getPublicKey()).build().decode(token);
    }
}
