package com.agentdoc.auth;

import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.service.IdentityBindingService;
import com.agentdoc.auth.service.JwtService;
import com.agentdoc.auth.service.PlatformRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 真实HTTP入口验证身份绑定仅对平台超管开放；业务存储使用模拟。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityBindingHttpSecurityTest {
    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;
    @MockitoBean private PlatformRoleService roles;
    @MockitoBean private IdentityBindingService bindings;

    @Test
    void anonymousAndOrdinaryUsersCannotBind() throws Exception {
        var user = new UserEntity();
        user.setId(9001L);
        user.setUsername("p0-user");
        String token = jwtService.createAccessToken(user, List.of());
        assertThat(post(null)).isEqualTo(401);
        when(roles.hasCurrentUserRole(anyString())).thenReturn(false, true);
        assertThat(post(token)).isEqualTo(403);
        assertThat(post(token)).isEqualTo(200);
        verify(bindings).bind("https://id.example/realm", "fixture-sub", 42L);
    }

    private int post(String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/platform/users/42/identities"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"issuer\":\"https://id.example/realm\",\"subject\":\"fixture-sub\"}"));
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
