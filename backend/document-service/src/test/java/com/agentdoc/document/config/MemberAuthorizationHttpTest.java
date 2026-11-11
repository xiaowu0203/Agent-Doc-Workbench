package com.agentdoc.document.config;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.config.CommonSecurityAutoConfiguration;
import com.agentdoc.common.config.CommonWebAutoConfiguration;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.document.controller.MemberController;
import com.agentdoc.document.controller.PlatformMembershipController;
import com.agentdoc.document.mapper.MemberMapper;
import com.agentdoc.document.mapper.PermissionMapper;
import com.agentdoc.document.mapper.SpaceRoleMapper;
import com.agentdoc.document.mapper.SpaceRolePermissionMapper;
import com.agentdoc.document.pojo.entity.MemberEntity;
import com.agentdoc.document.service.MemberService;
import com.agentdoc.document.service.SpacePermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 成员修改及跨空间平台查询的真实方法鉴权；依赖失败不能落入目标业务服务。 */
@WebMvcTest({MemberController.class, PlatformMembershipController.class})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ContextConfiguration(classes = {MemberController.class, PlatformMembershipController.class, SpacePermissionService.class,
        CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class MemberAuthorizationHttpTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private MemberService changes;
    @MockitoBean private MemberMapper members;
    @MockitoBean private SpaceRoleMapper roles;
    @MockitoBean private SpaceRolePermissionMapper permissions;
    @MockitoBean private PermissionMapper catalog;
    @MockitoBean private AuthIdentityFeign identity;
    @MockitoBean private TaskFeign task;
    @MockitoBean private TaskCapabilityVerifier verifier;
    @MockitoBean private JwtDecoder decoder;

    @Test void invalidIdentityAndRevokedMemberPermissionCannotWrite() throws Exception {
        when(decoder.decode("verified-fixture")).thenThrow(new BadJwtException("invalid"));
        update(401);
        human(List.of());
        update(403);
        verifyNoInteractions(changes);
    }

    @Test void permissionStorageFailureThroughExpressionReturns503() throws Exception {
        human(List.of());
        when(members.selectOne(any())).thenThrow(new DataAccessResourceFailureException("private SQL details"));
        update(503);
        verifyNoInteractions(changes);
    }

    @Test void crossSpacePlatformQueryKeepsRoleDependencyFailure503() throws Exception {
        human(List.of(SUPER_ADMIN));
        when(identity.checkPlatformRole("Bearer verified-fixture", SUPER_ADMIN)).thenThrow(new IllegalStateException("private"));
        mvc.perform(post("/api/document/platform/user-memberships/query").header("Authorization", "Bearer verified-fixture")
                        .contentType("application/json").content("{\"userIds\":[9003]}"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(changes, members);
    }

    @Test void currentMemberPermissionAllowsWrite() throws Exception {
        human(List.of());
        var member = new MemberEntity(); member.setRoleId(7001L);
        when(members.selectOne(any())).thenReturn(member);
        when(permissions.selectCount(any())).thenReturn(1L);
        update(200);
        verify(changes).changeRole(eq(7001L), eq(9003L), any());
    }

    private void update(int status) throws Exception {
        mvc.perform(put("/api/document/spaces/7001/members/9003").header("Authorization", "Bearer verified-fixture")
                        .contentType("application/json").content("{\"roleId\":7002}"))
                .andExpect(status().is(status));
    }

    private void human(List<String> roles) {
        var now = Instant.now();
        var jwt = Jwt.withTokenValue("verified-fixture").header("alg", "RS256").header("typ", "adw-human+jwt")
                .header("kid", "local-identity-fixture").claim("iss", "urn:agent-doc-workbench:local-identity")
                .subject("9002").audience(List.of("workbench-api")).issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("purpose", "HUMAN_ACCESS").claim("scope", "workbench:access").claim("actorType", "HUMAN")
                .claim("azp", "workbench-web").claim("auth_version", 0L).claim("sid", UUID.randomUUID().toString()).build();
        // 覆盖此前的失败桩，避免Mockito在when时调用已设置的异常。
        doReturn(jwt).when(decoder).decode("verified-fixture");
        when(identity.currentIdentity("Bearer verified-fixture")).thenReturn(Result.ok(new IdentitySelfVO(9002L, roles, 0L)));
    }
}
