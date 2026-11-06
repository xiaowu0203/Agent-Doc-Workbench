package com.agentdoc.auth.migration;

import com.agentdoc.auth.service.IdentityBindingService;
import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.auth.service.JwtService;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.security.OidcHumanAuthenticationConverter;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.config.IdentitySelfFeignErrorDecoder;
import com.agentdoc.common.feign.interceptor.AuthHeaderForwardInterceptor;
import com.agentdoc.common.security.OidcRemoteHumanAuthenticationConverter;
import com.agentdoc.common.config.CommonSecurityAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Request;
import feign.Retryer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
    @Autowired private MockMvc mvc;
    @LocalServerPort private int port;
    @MockitoBean private PlatformRoleService roles;

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
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(roles.hasCurrentUserRole(anyString())).thenReturn(false, true, true, true);
        assertThatThrownBy(() -> service.bind(ISSUER, SUBJECT, 9001L)).isInstanceOf(AccessDeniedException.class);
        service.bind(ISSUER, SUBJECT, 9001L);
        assertThat(service.resolve(ISSUER, SUBJECT)).isEqualTo(9001L);
        jdbc.update("UPDATE `user` SET status=0 WHERE id=9001");
        assertThatThrownBy(() -> service.resolve(ISSUER, SUBJECT)).isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE `user` SET status=1 WHERE id=9001");
        service.revoke(ISSUER, SUBJECT, 9001L);
        assertThatThrownBy(() -> service.resolve(ISSUER, SUBJECT)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.bind(ISSUER, SUBJECT, 9001L)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_id=9001 AND target_type='IDENTITY_BINDING' AND action IN ('IDENTITY_BOUND','IDENTITY_REVOKED')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM identity_binding WHERE user_id=9001", String.class)).isEqualTo("REVOKED");
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
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(operator));
        when(roles.hasCurrentUserRole(anyString())).thenReturn(true);
        when(roles.listRoleKeys(9002L)).thenReturn(List.of("LOCAL_ROLE"));
        service.bind(ISSUER, subject, 9002L);
        String python = "import sys,importlib.util,json;sys.dont_write_bytecode=True;"
                + "s=importlib.util.spec_from_file_location('p',sys.argv[1]);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);"
                + "m.KeycloakPocTest.setUpClass();p=m.KeycloakPocTest('test_authorization_code_cannot_be_reused');"
                + "c,v,_=p.authorization_code();r=p.exchange(c,v);assert r.status_code==200;t=r.json();sys.stdout.write(json.dumps({'access':t['access_token'],'id':t['id_token']}))";
        Process process = new ProcessBuilder("python", "-c", python, root.resolve("deploy/identity/Test-PocIdentity.py").toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); }
        }
        Map<?, ?> tokens = JsonUtils.parse(output, Map.class);
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
        assertThat(service.resolve(ISSUER, subject)).isEqualTo(9002L);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("p0-oidc"));
        var identityResponse = mvc.perform(get("/api/auth/identity").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<?, ?> identity = (Map<?, ?>) JsonUtils.parse(identityResponse, Map.class).get("data");
        assertThat(identity.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder("userId", "platformRoles");
        assertThat(identity.get("userId").toString()).isEqualTo("9002");
        AuthIdentityFeign identityClient = Feign.builder().contract(new SpringMvcContract())
                .decoder(new SpringDecoder(HttpMessageConverters::new)).errorDecoder(new IdentitySelfFeignErrorDecoder())
                .requestInterceptor(new AuthHeaderForwardInterceptor()).retryer(Retryer.NEVER_RETRY)
                .options(new Request.Options(2, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, false))
                .target(AuthIdentityFeign.class, "http://localhost:" + port);
        var remote = new OidcRemoteHumanAuthenticationConverter(identityClient);
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
        AuthIdentityFeign unavailableClient = Feign.builder().contract(new SpringMvcContract())
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
        try {
            setFixtureUserEnabled(root, false);
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        } finally {
            setFixtureUserEnabled(root, true);
        }
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + idToken)).andExpect(status().isUnauthorized());
        var localUser = new UserEntity();
        localUser.setId(9002L);
        localUser.setUsername("p0-oidc");
        String localToken = jwtService.createAccessToken(localUser, List.of());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + localToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(operator));
        service.revoke(ISSUER, subject, 9002L);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> remote.convert(jwt)).isInstanceOf(BadCredentialsException.class);
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
