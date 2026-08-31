package com.agentdoc.document.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.document.mapper.MemberMapper;
import com.agentdoc.document.mapper.PermissionMapper;
import com.agentdoc.document.mapper.SpaceRoleMapper;
import com.agentdoc.document.mapper.SpaceRolePermissionMapper;
import com.agentdoc.document.pojo.entity.MemberEntity;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static com.agentdoc.common.constant.JwtConstant.CLAIM_PLATFORM_ROLES;
import static com.agentdoc.common.constant.JwtConstant.CLAIM_SCOPE;
import static com.agentdoc.common.constant.JwtConstant.SCOPE_USER;
import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_MANAGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link SpacePermissionService} 核心权限回归测试。
 */
@ExtendWith(MockitoExtension.class)
class SpacePermissionServiceTest {

    private static final long USER_ID = 1001L;
    private static final long SPACE_ID = 2001L;
    private static final long ROLE_ID = 3001L;

    @Mock
    private MemberMapper memberMapper;
    @Mock
    private SpaceRoleMapper spaceRoleMapper;
    @Mock
    private SpaceRolePermissionMapper rolePermissionMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private AuthFeign authFeign;

    private SpacePermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new SpacePermissionService(memberMapper, spaceRoleMapper,
                rolePermissionMapper, permissionMapper, authFeign, null, null, null);
        login(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowSkillManageWithoutOwnerWhenRoleHasPermission() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member());
        when(rolePermissionMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertTrue(permissionService.hasPermission(SPACE_ID, SKILL_MANAGE));
        permissionService.requirePermission(SPACE_ID, SKILL_MANAGE);
    }

    @Test
    void shouldRejectSkillManageWhenRoleDoesNotHavePermission() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member());
        when(rolePermissionMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> permissionService.requirePermission(SPACE_ID, SKILL_MANAGE));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    @Test
    void platformSuperAdminShouldOnlyBypassCrossSpaceRead() {
        login(List.of(SUPER_ADMIN));
        when(authFeign.checkPlatformRole(SUPER_ADMIN)).thenReturn(Result.ok());
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertTrue(permissionService.hasPermission(SPACE_ID, SKILL_READ));
        assertFalse(permissionService.hasPermission(SPACE_ID, SKILL_MANAGE));
    }

    private MemberEntity member() {
        MemberEntity member = new MemberEntity();
        member.setSpaceId(SPACE_ID);
        member.setUserId(USER_ID);
        member.setRoleId(ROLE_ID);
        return member;
    }

    private void login(List<String> platformRoles) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(USER_ID))
                .claim(CLAIM_SCOPE, SCOPE_USER)
                .claim(CLAIM_PLATFORM_ROLES, platformRoles)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
