package com.agentdoc.auth;

import com.agentdoc.auth.service.IdentityBindingService;
import com.agentdoc.auth.service.JwtService;
import com.agentdoc.auth.support.LocalIdentityTestInitializer;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OidcHumanTokenUtils;
import com.agentdoc.common.utils.OidcServiceTokenUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

/** 独立临时CC客户端真实签发/停用；不调整P0已有客户端的300秒历史契约。 */
@EnabledIfEnvironmentVariable(named = "RUN_AUTH_P0_OIDC_POC", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OidcServiceCredentialIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private JwtService capabilities;
    @MockitoBean private IdentityBindingService bindings;

    @DynamicPropertySource
    static void oidc(DynamicPropertyRegistry properties) {
        String issuer = System.getenv("P0_TEST_ISSUER");
        if (!"http://localhost:18090/realms/agent-doc-workbench-p0".equals(issuer)) { throw new IllegalStateException("仅允许隔离OIDC夹具"); }
        properties.add("workbench.auth.mode", () -> "oidc");
        properties.add("workbench.auth.oidc.issuer", () -> issuer);
        properties.add("workbench.auth.oidc.introspection-uri", () -> issuer + "/protocol/openid-connect/token/introspect");
        properties.add("workbench.auth.oidc.introspection-client-secret", () -> System.getenv("P0_TEST_INTROSPECTION_SECRET"));
    }

    @Test
    void realServiceLifetimeAndClientStopCannotCreateAHumanIdentity() throws Exception {
        var fixture = serviceFixture();
        String access = (String) fixture.get("access"); String client = (String) fixture.get("client");
        var jwt = JwtDecoders.fromIssuerLocation(System.getenv("P0_TEST_ISSUER")).decode(access);
        OidcServiceTokenUtils.requireService(jwt, Instant.now(), Set.of(client));
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(60));
        assertThat(((Number) fixture.get("expiresIn")).intValue()).isEqualTo(60);
        assertThat(((Number) fixture.get("disabledStatus")).intValue()).isIn(400, 401);
        assertThat(OidcHumanTokenUtils.isHumanAccess(jwt)).isFalse();
        assertThatThrownBy(() -> OidcServiceTokenUtils.requireService(jwt, Instant.now(), Set.of("other-service"))).isInstanceOf(JwtException.class);
        var local = LocalIdentityTestInitializer.signer(capabilities);
        assertThatThrownBy(() -> local.humanDecoder().decode(access)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> local.serviceDecoder(Set.of(client)).decode(access)).isInstanceOf(JwtException.class);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/identity"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + access).GET().build();
        assertThat(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        verifyNoInteractions(bindings);
    }

    private Map<?, ?> serviceFixture() throws Exception {
        String python = """
                import sys, importlib.util, json, secrets
                sys.dont_write_bytecode = True
                spec = importlib.util.spec_from_file_location('fixture', sys.argv[1])
                f = importlib.util.module_from_spec(spec); spec.loader.exec_module(f)
                http, headers = f.admin_session()
                realm = 'http://localhost:18090/admin/realms/agent-doc-workbench-p0'
                assert f.require(http.get(realm, headers=headers, timeout=10), 200).json()['attributes']['adw.p0.fixture'] == 'v1'
                scopes = f.require(http.get(realm+'/client-scopes', headers=headers, timeout=10), 200).json()
                basic = next(s['id'] for s in scopes if s['name'] == 'basic')
                service = next((s['id'] for s in scopes if s['name'] == 'workbench:service'), None)
                created_scope = None; client_uuid = None
                try:
                    if service is None:
                        response = f.require(http.post(realm+'/client-scopes', headers=headers, timeout=10, json={
                            'name':'workbench:service', 'protocol':'openid-connect',
                            'attributes':{'include.in.token.scope':'true','adw.p1.fixture':'service-v1'}}),201)
                        service = response.headers['Location'].rsplit('/',1)[1]; created_scope = service
                    client = 'p1-service-'+secrets.token_hex(6); secret = secrets.token_urlsafe(32)
                    response = f.require(http.post(realm+'/clients', headers=headers, timeout=10, json={
                        'clientId':client,'enabled':True,'protocol':'openid-connect','publicClient':False,
                        'clientAuthenticatorType':'client-secret','secret':secret,'fullScopeAllowed':False,
                        'standardFlowEnabled':False,'implicitFlowEnabled':False,'directAccessGrantsEnabled':False,
                        'serviceAccountsEnabled':True,'authorizationServicesEnabled':False,
                        'defaultClientScopes':[],'optionalClientScopes':[],
                        'attributes':{'access.token.lifespan':'60','adw.p1.fixture':'service-v1'},
                        'protocolMappers':[{'name':'internal-audience','protocol':'openid-connect','protocolMapper':'oidc-audience-mapper',
                            'config':{'included.custom.audience':'workbench-internal','id.token.claim':'false','access.token.claim':'true'}}]}),201)
                    client_uuid = response.headers['Location'].rsplit('/',1)[1]; url = realm+'/clients/'+client_uuid
                    for scope in f.require(http.get(url+'/default-client-scopes',headers=headers,timeout=10),200).json():
                        f.require(http.delete(url+'/default-client-scopes/'+scope['id'],headers=headers,timeout=10),204)
                    for scope in (basic,service):
                        f.require(http.put(url+'/default-client-scopes/'+scope,headers=headers,timeout=10),204)
                    token_url = 'http://localhost:18090/realms/agent-doc-workbench-p0/protocol/openid-connect/token'
                    token = f.require(http.post(token_url,auth=(client,secret),data={'grant_type':'client_credentials','scope':'workbench:service'},timeout=10),200).json()
                    f.require(http.put(url,headers=headers,json={'enabled':False},timeout=10),204)
                    disabled = http.post(token_url,auth=(client,secret),data={'grant_type':'client_credentials'},timeout=10).status_code
                    output = {'access':token['access_token'],'client':client,'expiresIn':token['expires_in'],'disabledStatus':disabled}
                finally:
                    http, headers = f.admin_session()
                    if client_uuid: f.require(http.delete(realm+'/clients/'+client_uuid,headers=headers,timeout=10),204)
                    if created_scope: f.require(http.delete(realm+'/client-scopes/'+created_scope,headers=headers,timeout=10),204)
                sys.stdout.write(json.dumps(output))
                """;
        Path root = Path.of(System.getProperty("user.dir")).getParent().getParent();
        var process = new ProcessBuilder("python", "-c", python, root.resolve("deploy/identity/Prepare-PocRealm.py").toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            assertThat(process.waitFor(90, TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return JsonUtils.parse(output, Map.class);
        } finally { if (process.isAlive()) { process.destroyForcibly(); } }
    }
}
