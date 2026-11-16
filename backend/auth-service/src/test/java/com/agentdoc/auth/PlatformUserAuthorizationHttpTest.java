package com.agentdoc.auth;

import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.service.LocalIdentityJwtService;
import com.agentdoc.auth.service.PlatformRoleService;
import com.agentdoc.auth.service.PlatformUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 三个真实敏感入口在方法鉴权阶段拒绝，不能调用目标写服务。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlatformUserAuthorizationHttpTest {
    @LocalServerPort private int port;
    @Autowired private LocalIdentityJwtService identities;
    @MockitoBean private UserMapper userMapper;
    @MockitoSpyBean private PlatformRoleService roles;
    @MockitoBean private PlatformUserService changes;
    private UserEntity operator;
    private String token;

    @BeforeEach void fixture() {
        operator = new UserEntity(); operator.setId(9002L); operator.setUsername("p1-operator");
        operator.setStatus(1); operator.setAuthVersion(0L);
        token = identities.issueHuman(operator).value();
    }

    @Test void invalidIdentityCannotEnterAnySensitiveWrite() throws Exception {
        when(userMapper.selectById(9002L)).thenReturn(null);
        for (String action : List.of("status", "platform-roles", "auth-revocations")) {
            assertThat(call(action)).isEqualTo(401);
        }
        verifyNoInteractions(changes);
    }

    @Test void revokedRoleCannotUseProjectionFromAuthentication() throws Exception {
        when(userMapper.selectById(9002L)).thenReturn(operator);
        for (String action : List.of("status", "platform-roles", "auth-revocations")) {
            doReturn(List.of(SUPER_ADMIN), List.of()).when(roles).listRoleKeys(9002L);
            assertThat(call(action)).isEqualTo(403);
        }
        verifyNoInteractions(changes);
    }

    @Test void authorityFailureDuringMethodCheckReturns503AndDoesNotWrite() throws Exception {
        doReturn(List.of(SUPER_ADMIN)).when(roles).listRoleKeys(9002L);
        for (String action : List.of("status", "platform-roles", "auth-revocations")) {
            doReturn(operator).doThrow(new DataAccessResourceFailureException("private SQL details"))
                    .when(userMapper).selectById(9002L);
            assertThat(call(action)).isEqualTo(503);
        }
        verifyNoInteractions(changes);
    }

    private int call(String action) throws Exception {
        String body = action.equals("status") ? "{\"status\":0}" : "{\"roleKeys\":[]}";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/platform/users/9003/" + action))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        request.method(action.equals("auth-revocations") ? "POST" : "PUT", HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
