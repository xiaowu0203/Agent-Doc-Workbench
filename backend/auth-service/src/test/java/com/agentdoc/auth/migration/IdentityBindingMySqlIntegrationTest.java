package com.agentdoc.auth.migration;

import com.agentdoc.common.security.ResolvedHumanAuthentication;
import com.agentdoc.auth.service.IdentityBindingService;
import com.agentdoc.auth.service.AuthService;
import com.agentdoc.auth.service.PlatformUserService;
import com.agentdoc.auth.support.LegacyHumanCredentialFixture;
import com.agentdoc.auth.service.DepartmentService;
import com.agentdoc.auth.service.PlatformAuditLogService;
import com.agentdoc.auth.pojo.dto.PlatformUserStatusUpdateDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserPasswordResetDTO;
import com.agentdoc.auth.pojo.dto.ChangePasswordRequestDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserUpdateDTO;
import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.auth.service.JwtService;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.security.OidcHumanAuthenticationConverter;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.http.IdentitySelfFeignTransport;
import com.agentdoc.common.feign.config.IdentitySelfFeignErrorDecoder;
import com.agentdoc.common.feign.interceptor.AuthHeaderForwardInterceptor;
import com.agentdoc.common.security.RemoteHumanAuthenticationConverter;
import com.agentdoc.common.enums.AuthenticationMode;
import com.agentdoc.common.config.CommonSecurityAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** 隔离MySQL中的绑定、解析、撤销及审计真实装配；不操作业务库。 */
@EnabledIfEnvironmentVariable(named = "P0_IDENTITY_FRESH_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class IdentityBindingMySqlIntegrationTest {
    private static final String ISSUER = System.getenv().getOrDefault("P0_TEST_ISSUER", "https://id.example/realm");
    private static final String SUBJECT = "550e8400-e29b-41d4-a716-446655440000";
    @Autowired private IdentityBindingService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OidcHumanAuthenticationConverter converter;
    @Autowired private JwtService jwtService;
    @Autowired private IdentitySelfFeignTransport identityTransport;
    @Autowired private MockMvc mvc;
    @LocalServerPort private int port;
    @MockitoBean private PlatformRoleService roles;
    @MockitoSpyBean private PlatformUserService users;
    @Autowired private AuthService auth;
    @MockitoSpyBean private PasswordEncoder passwords;
    @MockitoSpyBean private DepartmentService departments;
    @MockitoSpyBean private PlatformAuditLogService audit;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("P0_IDENTITY_FRESH_URL");
        if (url == null || !url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):13316/adw_p0_identity_fresh_[a-f0-9]{12}\\?.*")) {
            throw new IllegalStateException("仅允许隔离身份测试库");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.username", () -> System.getenv("P0_IDENTITY_MYSQL_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("P0_IDENTITY_MYSQL_PASSWORD"));
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("workbench.auth.oidc.issuer", () -> ISSUER);
        properties.add("workbench.auth.oidc.introspection-uri", () -> ISSUER + "/protocol/openid-connect/token/introspect");
        properties.add("workbench.auth.oidc.introspection-client-secret", () -> System.getenv().getOrDefault("P0_TEST_INTROSPECTION_SECRET", ""));
        properties.add("workbench.auth.mode", () -> "true".equals(System.getenv("RUN_AUTH_P0_OIDC_POC")) ? "oidc" : "local");
        properties.add("logging.level.org.springframework.web.client.RestTemplate", () -> "DEBUG");
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void bindingIsAuditedAndCannotBeReboundAfterRevocation() {
        jdbc.update("INSERT INTO `user` (id, username, password_hash, status) VALUES (9001, 'p0-integration', 'fixture', 1)");
        Jwt jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("9001").claim("scope", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(jwt, Long.valueOf(jwt.getSubject()), List.of()));
        when(roles.hasCurrentUserRole(anyString())).thenReturn(false, true, true, true);
        assertThatThrownBy(() -> service.bind(ISSUER, SUBJECT, 9001L)).isInstanceOf(AccessDeniedException.class);
        service.bind(ISSUER, SUBJECT, 9001L);
        assertThat(service.resolve(ISSUER, SUBJECT).getId()).isEqualTo(9001L);
        jdbc.update("UPDATE `user` SET status=0 WHERE id=9001");
        assertThatThrownBy(() -> service.resolve(ISSUER, SUBJECT)).isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE `user` SET status=1 WHERE id=9001");
        service.revoke(ISSUER, SUBJECT, 9001L);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=9001", Long.class)).isEqualTo(1L);
        assertThatThrownBy(() -> service.resolve(ISSUER, SUBJECT)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.bind(ISSUER, SUBJECT, 9001L)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=9001", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_id=9001 AND target_type='IDENTITY_BINDING' AND action IN ('IDENTITY_BOUND','IDENTITY_REVOKED')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM identity_binding WHERE user_id=9001", String.class)).isEqualTo("REVOKED");
    }

    @Test
    void oidcStatusChangesRevokeButLocalPasswordOperationsAreForbidden() {
        jdbc.update("INSERT INTO `user` (id, username, password_hash, status) VALUES (9003, 'p1-version-events', ?, 1)",
                passwords.encode("initial-fixture-password"));
        Jwt jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("9003").claim("scope", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(jwt, Long.valueOf(jwt.getSubject()), List.of()));

        users.updateStatus(9003L, new PlatformUserStatusUpdateDTO(0));
        users.updateStatus(9003L, new PlatformUserStatusUpdateDTO(0));
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=9003", Long.class)).isEqualTo(1L);
        long boundary = jdbc.queryForObject("SELECT oidc_auth_not_before FROM `user` WHERE id=9003", Long.class);
        assertThat(boundary).isGreaterThan(Instant.now().getEpochSecond());
        users.updateStatus(9003L, new PlatformUserStatusUpdateDTO(1));
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=9003", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT oidc_auth_not_before FROM `user` WHERE id=9003", Long.class)).isEqualTo(boundary);

        assertThatThrownBy(() -> users.resetPassword(9003L, new PlatformUserPasswordResetDTO("reset-fixture-password")))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        assertThatThrownBy(() -> auth.changePassword(new ChangePasswordRequestDTO("initial-fixture-password", "changed-fixture-password")))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        assertThatThrownBy(() -> auth.changePassword(new ChangePasswordRequestDTO("wrong-fixture-password", "unused-fixture-password")))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=9003", Long.class)).isEqualTo(1L);
    }

    @Test
    void failedAuditRollsBackBothRevocationFieldsAndUnauthorisedServiceCallsAreRejected() {
        jdbc.update("INSERT INTO `user` (id, username, password_hash, status) VALUES (9007, 'p1-revocation-rollback', NULL, 1)");
        Jwt operator = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("9007").claim("scope", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(operator, Long.valueOf(operator.getSubject()), List.of()));
        when(roles.hasCurrentUserRole(anyString())).thenReturn(false);
        assertThatThrownBy(() -> users.forceLogout(9007L)).isInstanceOf(AccessDeniedException.class);
        when(roles.hasCurrentUserRole(anyString())).thenReturn(true);
        doThrow(new IllegalStateException("fixture audit unavailable")).when(audit)
                .record(eq("PLATFORM_USER_AUTHENTICATION_REVOKED"), anyString(), eq(9007L), anyMap());
        assertThatThrownBy(() -> users.forceLogout(9007L)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=9007", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT oidc_auth_not_before FROM `user` WHERE id=9007", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void oidcNeverInvokesLocalPasswordVerification(boolean disabled) {
        long userId = disabled ? 9004L : 9006L;
        String username = disabled ? "p1-login-disabled" : "p1-login-version";
        String hash = passwords.encode("login-fixture-password");
        jdbc.update("INSERT INTO `user` (id, username, password_hash, status) VALUES (?, ?, ?, ?)", userId, username, hash, disabled ? 0 : 1);

        assertThatThrownBy(() -> auth.authenticateLocalUser(username, "login-fixture-password"))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode()));
        verify(passwords, never()).matches("login-fixture-password", hash);
        assertThat(jdbc.queryForObject("SELECT status FROM `user` WHERE id=?", Integer.class, userId)).isEqualTo(disabled ? 0 : 1);
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=?", Long.class, userId)).isZero();
    }

    @Test
    void profileUpdateDoesNotWriteBackStaleAuthenticationFields() {
        jdbc.update("INSERT INTO `user` (id, username, password_hash, status) VALUES (9005, 'p1-profile-race', 'original-fixture-hash', 1)");
        Jwt jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("9005").claim("scope", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(jwt, Long.valueOf(jwt.getSubject()), List.of()));
        doAnswer(invocation -> {
            jdbc.update("UPDATE `user` SET status=0, password_hash='new-fixture-hash', auth_version=auth_version+1 WHERE id=9005");
            return null;
        }).when(departments).requireEnabledDepartment(null);

        users.update(9005L, new PlatformUserUpdateDTO("Updated profile", null, null, null));

        assertThat(jdbc.queryForObject("SELECT status FROM `user` WHERE id=9005", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM `user` WHERE id=9005", String.class)).isEqualTo("new-fixture-hash");
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=9005", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT nickname FROM `user` WHERE id=9005", String.class)).isEqualTo("Updated profile");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_OIDC_POC", matches = "true")
    void realKeycloakSubjectResolvesToExistingUserWithoutChangingJwt() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();
        Map<?, ?> fixture = JsonUtils.parse(Files.readString(root.resolve(".codex-validation-store/v0.3.0-p0/runtime/oidc-poc.json")), Map.class);
        assertThat(ISSUER).isEqualTo(fixture.get("issuer"));
        String subject = fixture.get("subject").toString();
        jdbc.update("INSERT INTO `user` (id, username, password_hash, status) VALUES (9002, 'p0-oidc', 'fixture', 1)");
        Jwt operator = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("9002").claim("scope", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(operator, Long.valueOf(operator.getSubject()), List.of()));
        when(roles.hasCurrentUserRole(anyString())).thenReturn(true);
        when(roles.listRoleKeys(9002L)).thenReturn(List.of("LOCAL_ROLE"));
        service.bind(ISSUER, subject, 9002L);
        Map<?, ?> tokens = keycloakTokens(root, "login", "");
        assertThat(tokens.get("active")).isEqualTo(true);
        String token = tokens.get("access").toString();
        String idToken = tokens.get("id").toString();
        var decoder = NimbusJwtDecoder.withJwkSetUri(ISSUER + "/protocol/openid-connect/certs").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        Jwt jwt = decoder.decode(token);
        var mapped = converter.convert(jwt);
        SecurityContextHolder.getContext().setAuthentication(mapped);
        assertThat(AuthUtils.getUserId()).isEqualTo(9002L);
        assertThat(AuthUtils.currentJwt().getSubject()).isEqualTo(subject);
        assertThat(AuthUtils.hasPlatformRole("LOCAL_ROLE")).isTrue();
        assertThat(service.resolve(ISSUER, subject).getId()).isEqualTo(9002L);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("p0-oidc"));
        var identityResponse = mvc.perform(get("/api/auth/identity").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<?, ?> identity = (Map<?, ?>) JsonUtils.parse(identityResponse, Map.class).get("data");
        assertThat(identity.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder("userId", "platformRoles", "authVersion");
        assertThat(identity.get("authVersion").toString()).isEqualTo("0");
        assertThat(identity.get("userId").toString()).isEqualTo("9002");
        AuthIdentityFeign identityClient = Feign.builder().client(identityTransport.client()).contract(new SpringMvcContract())
                .decoder(new SpringDecoder(HttpMessageConverters::new)).errorDecoder(new IdentitySelfFeignErrorDecoder())
                .requestInterceptor(new AuthHeaderForwardInterceptor()).retryer(Retryer.NEVER_RETRY)
                .options(new Request.Options(2, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, false))
                .target(AuthIdentityFeign.class, "http://localhost:" + port);
        var remote = new RemoteHumanAuthenticationConverter(identityClient, AuthenticationMode.OIDC);
        var remoteIdentity = remote.convert(jwt);
        assertThat(remoteIdentity.getUserId()).isEqualTo(9002L);
        assertThat(remoteIdentity.getPrincipal().getSubject()).isEqualTo(subject);
        assertThat(remoteIdentity.hasPlatformRole("LOCAL_ROLE")).isTrue();
        try (var business = new AnnotationConfigWebApplicationContext()) {
            business.setServletContext(new MockServletContext());
            business.getEnvironment().getPropertySources().addFirst(new MapPropertySource("p0", Map.of(
                    "workbench.auth.mode", "oidc", "workbench.auth.oidc.issuer", ISSUER)));
            business.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("authIdentityFeign", identityClient));
            business.register(BusinessProbeConfig.class);
            business.refresh();
            MockMvc businessMvc = MockMvcBuilders.webAppContextSetup(business).apply(springSecurity()).build();
            businessMvc.perform(get("/p0-business-probe").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(9002));
            businessMvc.perform(get("/p0-business-probe").header("Authorization", "Bearer " + idToken))
                    .andExpect(status().isUnauthorized());
        }
        AuthIdentityFeign unavailableClient = Feign.builder().client(identityTransport.client()).contract(new SpringMvcContract())
                .decoder(new SpringDecoder(HttpMessageConverters::new)).errorDecoder(new IdentitySelfFeignErrorDecoder())
                .requestInterceptor(new AuthHeaderForwardInterceptor()).retryer(Retryer.NEVER_RETRY)
                .options(new Request.Options(1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS, false))
                .target(AuthIdentityFeign.class, "http://localhost:1");
        try (var business = new AnnotationConfigWebApplicationContext()) {
            business.setServletContext(new MockServletContext());
            business.getEnvironment().getPropertySources().addFirst(new MapPropertySource("p0", Map.of(
                    "workbench.auth.mode", "oidc", "workbench.auth.oidc.issuer", ISSUER)));
            business.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("authIdentityFeign", unavailableClient));
            business.register(BusinessProbeConfig.class);
            business.refresh();
            MockMvc businessMvc = MockMvcBuilders.webAppContextSetup(business).apply(springSecurity()).build();
            businessMvc.perform(get("/p0-business-probe").header("Authorization", "Bearer " + token))
                    .andExpect(status().isServiceUnavailable());
        }
        // 无权限调用不得推进边界；有权限的本地强退不注销Keycloak会话。
        when(roles.hasCurrentUserRole(anyString())).thenReturn(false);
        mvc.perform(post("/api/platform/users/9002/auth-revocations").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=9002", Long.class)).isZero();
        when(roles.hasCurrentUserRole(anyString())).thenReturn(true);
        doThrow(new DataAccessResourceFailureException("fixture audit database unavailable")).doCallRealMethod().when(audit)
                .record(eq("PLATFORM_USER_AUTHENTICATION_REVOKED"), anyString(), eq(9002L), anyMap());
        mvc.perform(post("/api/platform/users/9002/auth-revocations").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=9002", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT oidc_auth_not_before FROM `user` WHERE id=9002", Long.class)).isZero();
        mvc.perform(post("/api/platform/users/9002/auth-revocations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT auth_version FROM `user` WHERE id=9002", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log "
                + "WHERE target_id=9002 AND action='PLATFORM_USER_AUTHENTICATION_REVOKED'", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        assertThat(keycloakTokens(root, "inspect", token).get("active")).isEqualTo(true);
        assertThatThrownBy(() -> remote.convert(jwt)).isInstanceOf(BadCredentialsException.class);
        Map<?, ?> refreshed = keycloakTokens(root, "refresh", tokens.get("refresh").toString());
        assertThat(refreshed.get("active")).isEqualTo(true);
        Jwt refreshedJwt = decoder.decode(refreshed.get("access").toString());
        assertThat(refreshedJwt.getClaims().get("auth_time")).isEqualTo(jwt.getClaims().get("auth_time"));
        mvc.perform(get("/api/auth/identity").header("Authorization", "Bearer " + refreshedJwt.getTokenValue()))
                .andExpect(status().isUnauthorized());
        long boundary = jdbc.queryForObject("SELECT oidc_auth_not_before FROM `user` WHERE id=9002", Long.class);
        long waitMillis = Duration.between(Instant.now(), Instant.ofEpochSecond(boundary + 1)).toMillis();
        assertThat(waitMillis).isLessThan(62_000);
        if (waitMillis > 0) { Thread.sleep(waitMillis); }
        Map<?, ?> silent = keycloakTokens(root, "silent", JsonUtils.toJson(tokens.get("cookies")));
        Jwt silentJwt = decoder.decode(silent.get("access").toString());
        assertThat(silentJwt.getIssuedAt().getEpochSecond()).isGreaterThan(boundary);
        assertThat(silentJwt.getClaims().get("auth_time")).isEqualTo(jwt.getClaims().get("auth_time"));
        mvc.perform(get("/api/auth/identity").header("Authorization", "Bearer " + silentJwt.getTokenValue()))
                .andExpect(status().isUnauthorized());
        Map<?, ?> fresh = keycloakTokens(root, "login", "");
        String freshToken = fresh.get("access").toString();
        Jwt freshJwt = decoder.decode(freshToken);
        assertThat(((Number) freshJwt.getClaims().get("auth_time")).longValue()).isGreaterThan(boundary);
        mvc.perform(get("/api/auth/identity").header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.authVersion").value(1));
        try {
            doThrow(new DataAccessResourceFailureException("fixture database unavailable")).when(users).requireEnabledUser(9002L);
            mvc.perform(get("/api/auth/identity").header("Authorization", "Bearer " + freshToken))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            doCallRealMethod().when(users).requireEnabledUser(9002L);
        }
        // 等待和新的独立认证都不能使原Access/旧Refresh产物恢复。
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + refreshedJwt.getTokenValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isForbidden());
        try {
            setFixtureUserEnabled(root, false);
            assertThat(keycloakTokens(root, "inspect", freshToken).get("active")).isEqualTo(false);
            assertThat(keycloakTokens(root, "refresh-status", fresh.get("refresh").toString()).get("status").toString()).isEqualTo("400");
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + freshToken)).andExpect(status().isUnauthorized());
        } finally {
            setFixtureUserEnabled(root, true);
        }
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + freshToken)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + idToken)).andExpect(status().isUnauthorized());
        var localUser = new UserEntity();
        localUser.setId(9002L);
        localUser.setUsername("p0-oidc");
        String localToken = LegacyHumanCredentialFixture.issue(jwtService, localUser);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + localToken)).andExpect(status().isUnauthorized());
        SecurityContextHolder.getContext().setAuthentication(new ResolvedHumanAuthentication(operator, Long.valueOf(operator.getSubject()), List.of()));
        service.revoke(ISSUER, subject, 9002L);
        assertThat(keycloakTokens(root, "inspect", freshToken).get("active")).isEqualTo(true);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + freshToken)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> remote.convert(freshJwt)).isInstanceOf(BadCredentialsException.class);
        Process logout = new ProcessBuilder("python", root.resolve("deploy/identity/Prepare-PocRealm.py").toString(),
                "--logout-fixture-user").redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            assertThat(logout.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(logout.exitValue()).isZero();
        } finally {
            if (logout.isAlive()) { logout.destroyForcibly(); }
        }
        assertThat(keycloakTokens(root, "inspect", freshToken).get("active")).isEqualTo(false);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + freshToken)).andExpect(status().isUnauthorized());
    }

    /** Token仅通过子进程管道传递，不进入命令行、日志或断言输出。 */
    private Map<?, ?> keycloakTokens(Path root, String mode, String refresh) throws Exception {
        String python = """
                import sys, os, importlib.util, json
                sys.dont_write_bytecode = True
                spec = importlib.util.spec_from_file_location('p', sys.argv[1])
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                module.KeycloakPocTest.setUpClass()
                probe = module.KeycloakPocTest('test_authorization_code_cannot_be_reused')
                request = json.load(sys.stdin)
                browser = module.LocalhostBrowserSession()
                if request['mode'] == 'silent':
                    for cookie in json.loads(request['refresh']):
                        browser.cookies.set_cookie(module.requests.cookies.create_cookie(**cookie))
                if request['mode'] == 'inspect':
                    active = probe.send(probe.http, 'POST', probe.discovery['introspection_endpoint'],
                        auth=('workbench-api', os.environ['P0_TEST_INTROSPECTION_SECRET']),
                        data={'token': request['refresh']})
                    assert active.status_code == 200
                    sys.stdout.write(json.dumps({'active': active.json()['active']}))
                    sys.exit(0)
                if request['mode'] in ('login', 'silent'):
                    code, verifier, _ = probe.authorization_code(browser=browser)
                    response = probe.exchange(code, verifier)
                else:
                    response = probe.send(probe.http, 'POST', probe.discovery['token_endpoint'],
                        auth=(probe.config['webClientId'], probe.config['webClientSecret']),
                        data={'grant_type': 'refresh_token', 'refresh_token': request['refresh']})
                if request['mode'] == 'refresh-status':
                    sys.stdout.write(json.dumps({'status': response.status_code}))
                    sys.exit(0)
                assert response.status_code == 200
                tokens = response.json()
                active = probe.send(probe.http, 'POST', probe.discovery['introspection_endpoint'],
                    auth=('workbench-api', os.environ['P0_TEST_INTROSPECTION_SECRET']),
                    data={'token': tokens['access_token']})
                assert active.status_code == 200
                sys.stdout.write(json.dumps({'access': tokens['access_token'], 'id': tokens.get('id_token', ''),
                    'refresh': tokens['refresh_token'], 'active': active.json()['active'],
                    'cookies': [dict(name=c.name, value=c.value, domain=c.domain, path=c.path,
                        secure=c.secure, expires=c.expires) for c in browser.cookies]}))
                """;
        Process process = new ProcessBuilder("python", "-c", python, root.resolve("deploy/identity/Test-PocIdentity.py").toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            try (var input = process.getOutputStream()) {
                input.write(JsonUtils.toJson(Map.of("mode", mode, "refresh", refresh)).getBytes(StandardCharsets.UTF_8));
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            return JsonUtils.parse(output, Map.class);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); }
        }
    }

    private void setFixtureUserEnabled(Path root, boolean enabled) throws Exception {
        String python = "import sys,importlib.util,json;sys.dont_write_bytecode=True;"
                + "s=importlib.util.spec_from_file_location('f',sys.argv[1]);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);"
                + "c=json.loads(m.CONFIG.read_text(encoding='utf-8'));h,headers=m.admin_session();"
                + "r='http://localhost:18090/admin/realms/agent-doc-workbench-p0';"
                + "realm=m.require(h.get(r,headers=headers,timeout=15),200).json();assert realm['attributes']['adw.p0.fixture']=='v1';"
                + "u=r+'/users/'+c['subject'];user=m.require(h.get(u,headers=headers,timeout=15),200).json();"
                + "assert user['username']==c['username'];m.require(h.put(u,headers=headers,json={'enabled':sys.argv[2]=='true'},timeout=15),204)";
        Process process = new ProcessBuilder("python", "-c", python, root.resolve("deploy/identity/Prepare-PocRealm.py").toString(),
                Boolean.toString(enabled)).redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(CommonSecurityAutoConfiguration.class)
    static class BusinessProbeConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean BusinessProbeController businessProbeController() { return new BusinessProbeController(); }
    }

    @RestController
    static class BusinessProbeController {
        @GetMapping("/p0-business-probe")
        Map<String, Object> current() { return Map.of("userId", AuthUtils.getUserIdOrException()); }
    }
}
