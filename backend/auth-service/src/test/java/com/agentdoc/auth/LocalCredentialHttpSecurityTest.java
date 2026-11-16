package com.agentdoc.auth;

import com.agentdoc.auth.config.LocalIdentityProperties;
import com.agentdoc.auth.constant.LocalIdentityConstant;
import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.service.LocalIdentityJwtService;
import com.agentdoc.auth.service.LocalLoginFailureLimiter;
import com.agentdoc.auth.service.PlatformAuditLogService;
import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.auth.support.LocalIdentityTestInitializer;
import com.agentdoc.common.constant.IdentityTokenConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际HTTP过滤链、真实RSA/BCrypt；存储故障可控注入，真实Redis另有独立并发验证。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LocalCredentialHttpSecurityTest {
    @DynamicPropertySource
    static void existingMachineBoundary(DynamicPropertyRegistry properties) {
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        properties.add("agent-doc.task-internal-auth.secret", () -> secret);
    }
    @BeforeAll
    static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "local-http-test");
        assistant.setCurrentNamespace(LocalCredentialHttpSecurityTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, UserEntity.class);
    }
    @LocalServerPort private int port;
    @Autowired private LocalIdentityJwtService signer;
    @Autowired private LocalIdentityProperties properties;
    @Autowired private PasswordEncoder encoder;
    @MockitoBean private UserMapper users;
    @MockitoBean private PlatformRoleService roles;
    @MockitoBean private LocalLoginFailureLimiter limiter;
    @MockitoBean private PlatformAuditLogService audit;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private UserEntity user;
    private final LocalLoginFailureLimiter.Reservation reservation = new LocalLoginFailureLimiter.Reservation(List.of("account", "ip"), "attempt");

    @BeforeEach
    void setup() {
        properties.getClients().get("task-service").setEnabled(true);
        user = new UserEntity(); user.setId(42L); user.setUsername("alice"); user.setNickname("Alice");
        user.setStatus(UserStatus.ENABLED.getCode()); user.setAuthVersion(3L); user.setPasswordHash(encoder.encode("secret1"));
        when(users.selectOne(any(Wrapper.class))).thenReturn(user);
        when(users.selectById(42L)).thenReturn(user);
        when(users.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(roles.listRoleKeys(42L)).thenReturn(List.of());
        when(limiter.reserve(anyString(), anyString())).thenReturn(reservation);
    }

    @Test
    void onlyBffMayIssueHumanAndCurrentVersionRejectsOldCredentials() throws Exception {
        assertThat(post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, null, login()).statusCode()).isEqualTo(401);
        assertThat(post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, service(), login()).statusCode()).isEqualTo(403);
        var response = post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, bff(), login());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        String token = credential(response);
        var jwt = signer.humanDecoder().decode(token);
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofDays(7));
        assertThat(get("/api/auth/identity", token).statusCode()).isEqualTo(200);
        for (String path : List.of("/api/auth/internal/task-capabilities", "/api/auth/internal/evaluation-worker-capabilities")) {
            assertThat(post(path, "Bearer " + token, Map.of()).statusCode()).isEqualTo(401);
        }
        user.setAuthVersion(4L);
        assertThat(get("/api/auth/identity", token).statusCode()).isEqualTo(401);
        user.setAuthVersion(3L); user.setStatus(UserStatus.DISABLED.getCode());
        assertThat(get("/api/auth/identity", token).statusCode()).isEqualTo(401);
        verify(limiter).release(reservation);
        verify(audit).recordAuthentication(eq("LOCAL_LOGIN_SUCCEEDED"), eq(42L), anyMap());
    }

    @Test
    void serviceProofIsNarrowAndDisabledSecretCannotMintAgain() throws Exception {
        var request = Map.of("audience", IdentityTokenConstant.SERVICE_AUDIENCE, "scopes", List.of(IdentityTokenConstant.SERVICE_SCOPE));
        assertThat(post(LocalIdentityConstant.SERVICE_PROOF_PATH, bff(), request).statusCode()).isEqualTo(403);
        var response = post(LocalIdentityConstant.SERVICE_PROOF_PATH, service(), request);
        assertThat(response.statusCode()).isEqualTo(200);
        String token = credential(response);
        var decoder = signer.serviceDecoder(Set.of("task-service"));
        var jwt = decoder.decode(token);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(60));
        assertThat(jwt.getClaims()).doesNotContainKeys("userId", "platformRoles", "auth_version", "sid");
        assertThat(get("/api/auth/identity", token).statusCode()).isEqualTo(401);
        properties.getClients().get("task-service").setEnabled(false);
        try {
            assertThat(post(LocalIdentityConstant.SERVICE_PROOF_PATH, service(), request).statusCode()).isEqualTo(401);
            decoder.decode(token);
        } finally { properties.getClients().get("task-service").setEnabled(true); }
        verifyNoInteractions(users, roles, limiter);
    }

    @Test
    void callerCannotChooseSubjectAuthorityScopeOrLifetime() throws Exception {
        for (String field : List.of("userId", "actions", "ttlSeconds", "platformRoles")) {
            var request = new HashMap<String, Object>(login()); request.put(field, "untrusted");
            assertThat(post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, bff(), request).statusCode()).isEqualTo(400);
        }
        for (var request : List.of(Map.of("audience", "other", "scopes", List.of(IdentityTokenConstant.SERVICE_SCOPE)),
                Map.of("audience", IdentityTokenConstant.SERVICE_AUDIENCE, "scopes", List.of("admin")),
                Map.of("audience", IdentityTokenConstant.SERVICE_AUDIENCE, "scopes", List.of(IdentityTokenConstant.SERVICE_SCOPE), "ttlSeconds", 3600))) {
            assertThat(post(LocalIdentityConstant.SERVICE_PROOF_PATH, service(), request).statusCode()).isEqualTo(400);
        }
        verifyNoInteractions(users, roles, limiter);
    }

    @Test
    void unknownDisabledAndWrongPasswordShareSameFailureAndKeepReservation() throws Exception {
        var wrong = Map.of("username", "alice", "password", "wrongpw", "clientIp", "127.0.0.1");
        var failure = post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, bff(), wrong);
        requireGenericLoginFailure(failure);
        user.setStatus(UserStatus.DISABLED.getCode());
        requireGenericLoginFailure(post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, bff(), login()));
        when(users.selectOne(any(Wrapper.class))).thenReturn(null);
        requireGenericLoginFailure(post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, bff(), login()));
        verify(limiter, never()).release(any());
    }

    @Test
    void rateLimitRedisDatabaseAndAuditFailuresDoNotReturnCredentials() throws Exception {
        when(limiter.reserve(anyString(), anyString())).thenThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS));
        assertThat(post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, bff(), login()).statusCode()).isEqualTo(429);
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE)).when(limiter).reserve(anyString(), anyString());
        assertThat(post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, bff(), login()).statusCode()).isEqualTo(503);
        verifyNoInteractions(users);
        doReturn(reservation).when(limiter).reserve(anyString(), anyString());
        when(users.selectOne(any(Wrapper.class))).thenThrow(new DataAccessResourceFailureException("private database"));
        assertThat(post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, bff(), login()).statusCode()).isEqualTo(503);
        doReturn(user).when(users).selectOne(any(Wrapper.class));
        doThrow(new DataAccessResourceFailureException("private audit")).when(audit).recordAuthentication(eq("LOCAL_LOGIN_SUCCEEDED"), eq(42L), anyMap());
        var failed = post(LocalIdentityConstant.HUMAN_CREDENTIAL_PATH, bff(), login());
        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(failed.body().contains("credential") || failed.body().contains("private")).isFalse();
        when(users.selectById(42L)).thenThrow(new DataAccessResourceFailureException("private identity"));
        assertThat(get("/api/auth/identity", signer.issueHuman(user).value()).statusCode()).isEqualTo(503);
    }

    @Test
    void oldBrowserProtocolIsClosedAndJwksDoesNotExposePrivateKey() throws Exception {
        for (String path : List.of("register", "login", "refresh", "logout")) {
            assertThat(post("/api/auth/" + path, null, Map.of()).statusCode()).isIn(401, 403);
            assertThat(post("/api/auth/" + path, "Bearer " + signer.issueHuman(user).value(), Map.of()).statusCode()).isEqualTo(403);
        }
        var response = get("/oauth2/identity-jwks", null);
        assertThat(response.statusCode()).isEqualTo(200);
        var tree = JsonUtils.parse(response.body(), JsonNode.class);
        assertThat(tree.path("keys").get(0).has("d")).isFalse();
        assertThat(tree.path("keys").get(0).has("n")).isTrue();
    }

    private Map<String, String> login() { return Map.of("username", "alice", "password", "secret1", "clientIp", "127.0.0.1"); }
    private void requireGenericLoginFailure(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        var body = JsonUtils.parse(response.body(), JsonNode.class);
        assertThat(body.path("code").asInt()).isEqualTo(ErrorCode.LOGIN_FAILED.getCode());
        assertThat(ErrorCode.LOGIN_FAILED.getMessage().equals(body.path("message").asText())).isTrue();
        assertThat(body.path("data").isNull()).isTrue();
    }
    private String bff() { return basic("workbench-web", LocalIdentityTestInitializer.BFF_SECRET); }
    private String service() { return basic("task-service", LocalIdentityTestInitializer.SERVICE_SECRET); }
    private String basic(String client, String secret) { return "Basic " + Base64.getEncoder().encodeToString((client + ":" + secret).getBytes(StandardCharsets.UTF_8)); }
    private String credential(HttpResponse<String> response) {
        return JsonUtils.parse(response.body(), JsonNode.class).path("data").path("credential").asText();
    }
    private HttpResponse<String> post(String path, String authorization, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body)));
        if (authorization != null) { request.header("Authorization", authorization); }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10)).GET();
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
