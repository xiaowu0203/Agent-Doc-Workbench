package com.agentdoc.common.utils;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static com.agentdoc.common.constant.JwtConstant.CLAIM_ACTOR_TYPE;
import static com.agentdoc.common.constant.JwtConstant.CLAIM_PLATFORM_ROLES;
import static com.agentdoc.common.constant.JwtConstant.CLAIM_SCOPE;
import static com.agentdoc.common.constant.JwtConstant.CLAIM_TASK_ID;
import static com.agentdoc.common.constant.JwtConstant.ACTOR_AGENT;
import static com.agentdoc.common.constant.JwtConstant.SCOPE_AGENT;
import static com.agentdoc.common.constant.JwtConstant.SCOPE_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthUtils} 单元测试：从 SecurityContext 读取身份（已认证 / 匿名 / Agent）。
 */
class AuthUtilsTest {

    private static final long USER_ID_FOR_PLATFORM_ROLE = 1004L;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 构造以 Jwt 为 principal 的认证信息并放入 SecurityContext */
    private void login(long subjectId, String agentId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(subjectId))
                .claim("username", "tester")
                .claim(CLAIM_SCOPE, agentId == null ? SCOPE_USER : SCOPE_AGENT);
        if (agentId != null) {
            builder.claim("agentId", agentId);
            builder.claim(CLAIM_ACTOR_TYPE, ACTOR_AGENT);
            builder.claim(CLAIM_TASK_ID, subjectId);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(builder.build(), null, List.of()));
    }

    @Test
    void shouldReadUserIdWhenAuthenticated() {
        login(1001L, null);
        assertEquals(1001L, AuthUtils.getUserId());
        assertEquals("1001", AuthUtils.currentJwt().getSubject());
    }

    @Test
    void shouldReturnNullWhenAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertNull(AuthUtils.getUserId());
        assertNull(AuthUtils.currentJwt());
        assertNull(AuthUtils.getAgentId());
    }

    @Test
    void shouldReturnNullWhenNoAuthentication() {
        assertNull(AuthUtils.getUserId());
        assertNull(AuthUtils.currentJwt());
    }

    @Test
    void shouldReadAgentIdWhenAgentClaimPresent() {
        login(1002L, "5001");
        assertNull(AuthUtils.getUserId());
        assertEquals(1002L, AuthUtils.getTaskId());
        assertEquals(5001L, AuthUtils.getAgentId());
    }

    @Test
    void shouldReturnNullAgentIdWhenClaimAbsent() {
        login(1003L, null);
        assertNull(AuthUtils.getAgentId());
        assertNull(AuthUtils.getTaskId());
    }

    @Test
    void shouldGetUserIdOrExceptionWhenAuthenticated() {
        login(1001L, null);
        assertEquals(1001L, AuthUtils.getUserIdOrException());
    }

    @Test
    void shouldThrowWhenGetUserIdOrExceptionAnonymous() {
        BusinessException ex = assertThrows(BusinessException.class, AuthUtils::getUserIdOrException);
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void shouldGetAgentIdOrExceptionWhenAgentClaimPresent() {
        login(1002L, "5001");
        assertEquals(5001L, AuthUtils.getAgentIdOrException());
    }

    @Test
    void shouldThrowWhenGetAgentIdOrExceptionClaimAbsent() {
        login(1003L, null);
        BusinessException ex = assertThrows(BusinessException.class, AuthUtils::getAgentIdOrException);
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void shouldRejectAgentAsUser() {
        login(1002L, "5001");
        assertNull(AuthUtils.getUserId());
        assertThrows(BusinessException.class, AuthUtils::getUserIdOrException);
    }

    @Test
    void shouldReadPlatformRoleFromHumanJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(USER_ID_FOR_PLATFORM_ROLE))
                .claim(CLAIM_SCOPE, SCOPE_USER)
                .claim(CLAIM_PLATFORM_ROLES, List.of("PLATFORM_SUPER_ADMIN"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));

        assertTrue(AuthUtils.hasPlatformRole("PLATFORM_SUPER_ADMIN"));
        assertFalse(AuthUtils.hasPlatformRole("OTHER_ROLE"));
    }
}
