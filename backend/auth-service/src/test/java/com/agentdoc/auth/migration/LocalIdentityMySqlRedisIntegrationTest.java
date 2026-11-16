package com.agentdoc.auth.migration;

import com.agentdoc.auth.constant.LocalIdentityConstant;
import com.agentdoc.auth.pojo.dto.ChangePasswordRequestDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserPasswordResetDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserStatusUpdateDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserRoleReplaceDTO;
import com.agentdoc.auth.service.AuthService;
import com.agentdoc.auth.service.LocalIdentityJwtService;
import com.agentdoc.auth.service.LocalLoginFailureLimiter;
import com.agentdoc.auth.service.PlatformUserService;
import com.agentdoc.auth.support.LocalIdentityTestInitializer;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.security.ResolvedHumanAuthentication;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.agentdoc.auth.health.AuthenticationReadinessHealthIndicator;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.net.ServerSocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.http.IdentitySelfFeignTransport;
import com.agentdoc.common.feign.config.IdentitySelfFeignErrorDecoder;
import com.agentdoc.common.config.CommonSecurityAutoConfiguration;
import com.agentdoc.common.utils.AuthUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Retryer;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 真实MySQL5.7/Redis及HTTP安全链；只有严格命名的隔离随机库可以运行。 */
@EnabledIfEnvironmentVariable(named = "P1_IDENTITY_LOCAL_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.location=file:./src/main/resources/application.yml")
class LocalIdentityMySqlRedisIntegrationTest {
    private static final String NAMESPACE = "adw:p1:local-http:" + UUID.randomUUID();
    private static final long USER_ID = 9871L;
    private static final long ADMIN_ID = 9870L;
    private static final Set<String> OWNED_KEYS = ConcurrentHashMap.newKeySet();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private LocalIdentityJwtService signer;
    @Autowired private PlatformUserService users;
    @Autowired private AuthService authentication;
    @MockitoSpyBean private PasswordEncoder passwords;
    @MockitoSpyBean private LocalLoginFailureLimiter limiter;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private AuthenticationReadinessHealthIndicator readiness;
    @MockitoBean private AuthIdentityFeign recursiveIdentity;

    @DynamicPropertySource
    static void isolatedDependencies(DynamicPropertyRegistry properties) {
        String url = System.getenv("P1_IDENTITY_LOCAL_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:13316/adw_p1_identity_local_[a-f0-9]{12}\\?.+")) {
            throw new IllegalStateException("只允许本批随机local测试库");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("P1_IDENTITY_MYSQL_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("P1_IDENTITY_MYSQL_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("auth.identity.login-rate-namespace", () -> NAMESPACE);
        properties.add("logging.level.com.agentdoc.auth.mapper", () -> "INFO");
    }

    @BeforeEach
    void fixtures() {
        jdbc.update("DELETE FROM user_platform_role WHERE user_id=9873");
        jdbc.update("DELETE FROM `user` WHERE id=9873");
        String hash = passwords.encode("initial-secret1");
        for (var entry : Map.of(USER_ID, "p1_local_user", ADMIN_ID, "p1_local_admin").entrySet()) {
            jdbc.update("INSERT INTO `user` (id,username,password_hash,status) VALUES (?,?,?,1) "
                    + "ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash),status=1,auth_version=0,oidc_auth_not_before=0", entry.getKey(), entry.getValue(), hash);
        }
        jdbc.update("INSERT IGNORE INTO user_platform_role (id,user_id,role_id) SELECT 9870,?,id FROM platform_role WHERE role_key='PLATFORM_SUPER_ADMIN'", ADMIN_ID);
        doAnswer(invocation -> {
            var reservation = (LocalLoginFailureLimiter.Reservation) invocation.callRealMethod();
            OWNED_KEYS.addAll(reservation.keys()); return reservation;
        }).when(limiter).reserve(anyString(), anyString());
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (!OWNED_KEYS.isEmpty()) { redis.delete(List.copyOf(OWNED_KEYS)); OWNED_KEYS.clear(); }
    }

    @Test
    void forceLogoutDisableReenableAndPasswordChangesRejectOldSignedAccess() throws Exception {
        assertThat(get("/actuator/health/readiness", null).statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/liveness", null).statusCode()).isEqualTo(200);
        String old = login("initial-secret1");
        assertThat(get("/api/auth/identity", old).statusCode()).isEqualTo(200);
        administrator(); users.forceLogout(USER_ID);
        assertThat(get("/api/auth/identity", old).statusCode()).isEqualTo(401);
        String next = login("initial-secret1");
        assertThat(get("/api/auth/identity", next).statusCode()).isEqualTo(200);
        users.updateStatus(USER_ID, new PlatformUserStatusUpdateDTO(0));
        users.updateStatus(USER_ID, new PlatformUserStatusUpdateDTO(1));
        assertThat(get("/api/auth/identity", next).statusCode()).isEqualTo(401);
        users.resetPassword(USER_ID, new PlatformUserPasswordResetDTO("reset-secret1"));
        assertThat(post("p1_local_user", "initial-secret1", "127.0.0.1").statusCode()).isEqualTo(401);
        String reset = login("reset-secret1");
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(signer.humanDecoder().decode(reset), USER_ID, List.of()));
        authentication.changePassword(new ChangePasswordRequestDTO("reset-secret1", "changed-secret1"));
        assertThat(get("/api/auth/identity", reset).statusCode()).isEqualTo(401);
        assertThat(get("/api/auth/identity", login("changed-secret1")).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=?", Long.class, USER_ID)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_id=? AND action='LOCAL_LOGIN_SUCCEEDED'", Long.class, USER_ID)).isGreaterThanOrEqualTo(4);
        var details = jdbc.queryForList("SELECT detail FROM audit_log WHERE target_type='AUTHENTICATION'", String.class);
        assertThat(details.stream().filter(Objects::nonNull).noneMatch(value -> value.contains("secret1") || value.contains("eyJ") || value.contains("$2a$"))).isTrue();
    }

    @Test
    void realAccountLimitAppliesToKnownUnknownNullHashAndEmptyPassword() throws Exception {
        for (String account : List.of("p1_local_user", "p1_unknown_user", "p1_null_hash")) {
            if (account.equals("p1_null_hash")) { jdbc.update("INSERT INTO `user` (id,username,password_hash,status) VALUES (9872,?,NULL,1)", account); }
            for (int attempt = 0; attempt < 5; attempt++) {
                assertThat(post(account, attempt == 0 ? "" : "wrong-secret1", "127.0.0.1").statusCode()).isEqualTo(401);
            }
            assertThat(post(account, "initial-secret1", "127.0.0.2").statusCode()).isEqualTo(429);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void latePasswordCheckCannotOverwriteConcurrentDisableOrRevocation(boolean disabled) {
        String hash = jdbc.queryForObject("SELECT password_hash FROM `user` WHERE id=?", String.class, USER_ID);
        doAnswer(invocation -> {
            jdbc.update("UPDATE `user` SET status=?,auth_version=auth_version+1 WHERE id=?", disabled ? 0 : 1, USER_ID);
            return invocation.callRealMethod();
        }).when(passwords).matches("initial-secret1", hash);
        assertThatThrownBy(() -> authentication.authenticateLocalUser("p1_local_user", "initial-secret1"))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.LOGIN_FAILED.getCode()));
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=?", Long.class, USER_ID)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM `user` WHERE id=?", Integer.class, USER_ID)).isEqualTo(disabled ? 0 : 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISABLE", "REVOKE", "MIXED"})
    void concurrentAdminRemovalsCannotRemoveBothLastAdministrators(String action) throws Exception {
        jdbc.update("INSERT INTO `user` (id,username,password_hash,status) VALUES (9873,'p1_other_admin',?,1)", passwords.encode("initial-secret1"));
        jdbc.update("INSERT INTO user_platform_role (id,user_id,role_id) SELECT 9873,9873,id FROM platform_role WHERE role_key='PLATFORM_SUPER_ADMIN'");
        String operatorToken = signer.issueHuman(users.requireUser(ADMIN_ID)).value();
        var snapshots = new CyclicBarrier(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(ADMIN_ID, 9873L).stream().map(target -> executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(
                        signer.humanDecoder().decode(operatorToken), ADMIN_ID, List.of("PLATFORM_SUPER_ADMIN")));
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("测试未同时启动"); }
                    return new TransactionTemplate(transactions).execute(status -> {
                        // 两个外层事务先形成相同的RR快照，验证保护必须读取锁后的当前状态。
                        jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE status=1", Long.class);
                        try { snapshots.await(10, TimeUnit.SECONDS); }
                        catch (Exception error) { throw new IllegalStateException("并发测试屏障未到齐", error); }
                        if ("DISABLE".equals(action) || "MIXED".equals(action) && target.equals(ADMIN_ID)) {
                            users.updateStatus(target, new PlatformUserStatusUpdateDTO(0));
                        } else { users.replaceRoles(target, new PlatformUserRoleReplaceDTO(List.of())); }
                        return 0;
                    });
                } catch (BusinessException error) { return error.getCode(); }
                finally { SecurityContextHolder.clearContext(); }
            })).toList();
            start.countDown();
            assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, ErrorCode.CONFLICT.getCode());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `user` u JOIN user_platform_role b ON b.user_id=u.id "
                + "JOIN platform_role r ON r.id=b.role_id WHERE u.deleted=0 AND u.status=1 AND r.deleted=0 AND r.role_key='PLATFORM_SUPER_ADMIN'", Long.class)).isEqualTo(1L);
    }

    @Test
    void unreachableRedisRejectsLoginAndReadinessButLivenessAndRecoveryWork() throws Exception {
        int closedPort;
        try (var socket = new ServerSocket(0)) { closedPort = socket.getLocalPort(); }
        var options = ClientOptions.builder().autoReconnect(false)
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(200)).build()).build();
        var configuration = LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).clientOptions(options).build();
        var unavailable = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", closedPort), configuration);
        unavailable.afterPropertiesSet(); unavailable.start();
        var unreachable = new StringRedisTemplate(unavailable);
        try {
            ReflectionTestUtils.setField(limiter, "redis", unreachable);
            ReflectionTestUtils.setField(readiness, "redis", unreachable);
            for (String account : List.of("p1_local_user", "p1_unknown_user")) {
                var response = post(account, "initial-secret1", "127.0.0.1");
                assertThat(response.statusCode()).isEqualTo(503);
                assertThat(response.body().contains("credential") || response.body().contains("initial-secret1")).isFalse();
            }
            assertThat(get("/actuator/health/readiness", null).statusCode()).isEqualTo(503);
            assertThat(get("/actuator/health/liveness", null).statusCode()).isEqualTo(200);
        } finally {
            ReflectionTestUtils.setField(limiter, "redis", redis);
            ReflectionTestUtils.setField(readiness, "redis", redis);
            unavailable.destroy();
        }
        assertThat(get("/actuator/health/readiness", null).statusCode()).isEqualTo(200);
        assertThat(get("/api/auth/identity", login("initial-secret1")).statusCode()).isEqualTo(200);
    }

    @Test
    void realAuditStorageFailureDoesNotExposeCredentialOrCommitRevocation() throws Exception {
        String previous = login("initial-secret1");
        String admin = signer.issueHuman(users.requireUser(ADMIN_ID)).value();
        jdbc.execute("RENAME TABLE audit_log TO p1_unavailable_audit_log");
        try {
            var failedLogin = post("p1_local_user", "initial-secret1", "127.0.0.1");
            assertThat(failedLogin.statusCode()).isEqualTo(503);
            assertThat(failedLogin.body().contains("credential")).isFalse();
            assertThat(revoke(admin).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=?", Long.class, USER_ID)).isZero();
            assertThat(jdbc.queryForObject("SELECT oidc_auth_not_before FROM `user` WHERE id=?", Long.class, USER_ID)).isZero();
        } finally { jdbc.execute("RENAME TABLE p1_unavailable_audit_log TO audit_log"); }
        assertThat(get("/api/auth/identity", previous).statusCode()).isEqualTo(200);
        assertThat(revoke(admin).statusCode()).isEqualTo(200);
        assertThat(get("/api/auth/identity", previous).statusCode()).isEqualTo(401);
        assertThat(get("/api/auth/identity", login("initial-secret1")).statusCode()).isEqualTo(200);
    }

    @Test
    void localBusinessHttpRechecksIdentityRolesRevocationAndAuthAvailabilityWithoutRecursion() throws Exception {
        String token = login("initial-secret1");
        try (var transport = new IdentitySelfFeignTransport(); var business = businessProbe(transport, "http://127.0.0.1:" + port)) {
            var response = businessGet(business, token);
            assertThat(response.statusCode()).isEqualTo(200);
            var body = JsonUtils.parse(response.body(), JsonNode.class);
            assertThat(body.path("userId").asLong()).isEqualTo(USER_ID);
            assertThat(body.path("superAdmin").asBoolean()).isFalse();
            administrator(); users.replaceRoles(USER_ID, new PlatformUserRoleReplaceDTO(List.of("PLATFORM_SUPER_ADMIN")));
            assertThat(JsonUtils.parse(businessGet(business, token).body(), JsonNode.class).path("superAdmin").asBoolean()).isTrue();
            users.replaceRoles(USER_ID, new PlatformUserRoleReplaceDTO(List.of()));
            assertThat(JsonUtils.parse(businessGet(business, token).body(), JsonNode.class).path("superAdmin").asBoolean()).isFalse();
            users.forceLogout(USER_ID);
            assertThat(businessGet(business, token).statusCode()).isEqualTo(401);
            assertThat(businessGet(business, login("initial-secret1")).statusCode()).isEqualTo(200);
        }
        int closedPort; try (var socket = new ServerSocket(0)) { closedPort = socket.getLocalPort(); }
        try (var transport = new IdentitySelfFeignTransport(); var business = businessProbe(transport, "http://127.0.0.1:" + closedPort)) {
            assertThat(businessGet(business, login("initial-secret1")).statusCode()).isEqualTo(503);
        }
        verifyNoInteractions(recursiveIdentity);
    }

    private AnnotationConfigServletWebServerApplicationContext businessProbe(IdentitySelfFeignTransport transport, String auth) {
        var client = Feign.builder().client(transport.client()).contract(new SpringMvcContract()).retryer(Retryer.NEVER_RETRY)
                .errorDecoder(new IdentitySelfFeignErrorDecoder()).decoder(new SpringDecoder(() -> new HttpMessageConverters()))
                .target(AuthIdentityFeign.class, auth);
        var context = new AnnotationConfigServletWebServerApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("p1-local-business", Map.of(
                "workbench.auth.mode", "local", "agent-doc.security.identity-jwks-url", "http://127.0.0.1:" + port + "/oauth2/identity-jwks")));
        context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("authIdentityFeign", client));
        context.register(BusinessProbeConfig.class);
        try { context.refresh(); return context; }
        catch (RuntimeException error) { context.close(); throw error; }
    }

    private HttpResponse<String> businessGet(AnnotationConfigServletWebServerApplicationContext business, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + business.getWebServer().getPort() + "/p1-local-business"))
                .timeout(Duration.ofSeconds(12)).header("Authorization", "Bearer " + token).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(CommonSecurityAutoConfiguration.class)
    static class BusinessProbeConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean TomcatServletWebServerFactory servletWebServerFactory() { return new TomcatServletWebServerFactory(0); }
        @Bean DispatcherServlet dispatcherServlet(WebApplicationContext context) { return new DispatcherServlet(context); }
        @Bean ServletRegistrationBean<DispatcherServlet> dispatcherRegistration(DispatcherServlet dispatcher) {
            return new ServletRegistrationBean<>(dispatcher, "/");
        }
        @Bean BusinessProbeController businessProbeController() { return new BusinessProbeController(); }
    }

    @RestController
    static class BusinessProbeController {
        @GetMapping("/p1-local-business")
        Map<String, Object> current() { return Map.of("userId", AuthUtils.getUserIdOrException(), "superAdmin", AuthUtils.hasPlatformRole("PLATFORM_SUPER_ADMIN")); }
    }

    private void administrator() {
        var admin = users.requireUser(ADMIN_ID);
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(signer.humanDecoder().decode(signer.issueHuman(admin).value()), ADMIN_ID, List.of("PLATFORM_SUPER_ADMIN")));
    }
    private String login(String password) throws Exception {
        var response = post("p1_local_user", password, "127.0.0.1");
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonUtils.parse(response.body(), JsonNode.class).path("data").path("credential").asText();
    }
    private HttpResponse<String> post(String account, String password, String ip) throws Exception {
        String basic = Base64.getEncoder().encodeToString(("workbench-web:" + LocalIdentityTestInitializer.BFF_SECRET).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + LocalIdentityConstant.HUMAN_CREDENTIAL_PATH))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Basic " + basic).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(Map.of("username", account, "password", password, "clientIp", ip)))).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10)).GET();
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> revoke(String admin) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/platform/users/" + USER_ID + "/auth-revocations"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + admin).POST(HttpRequest.BodyPublishers.noBody()).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
