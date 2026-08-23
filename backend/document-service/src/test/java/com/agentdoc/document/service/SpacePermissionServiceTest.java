package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.document.enums.SpaceRole;
import com.agentdoc.document.mapper.MemberMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link SpacePermissionService} 单元测试：空间成员角色校验逻辑。
 */
@ExtendWith(MockitoExtension.class)
class SpacePermissionServiceTest {

    private static final long USER_ID = 1001L;
    private static final long SPACE_ID = 2001L;

    @Mock
    private MemberMapper memberMapper;

    private SpacePermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new SpacePermissionService(memberMapper);
        // 模拟已登录：SecurityContext 放入以 Jwt 为 principal 的认证信息
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(USER_ID))
                .claim("username", "tester")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 构造指定角色的成员实体 */
    private MemberEntity member(SpaceRole role) {
        MemberEntity entity = new MemberEntity();
        entity.setSpaceId(SPACE_ID);
        entity.setUserId(USER_ID);
        entity.setRole(role.getCode());
        return entity;
    }

    @Test
    void shouldReturnNullRoleWhenNotMember() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertNull(permissionService.getRole(SPACE_ID, USER_ID));
    }

    @Test
    void shouldReturnRoleWhenMember() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member(SpaceRole.EDITOR));
        assertEquals(SpaceRole.EDITOR, permissionService.getRole(SPACE_ID, USER_ID));
    }

    @Test
    void shouldRejectNonMemberWithForbidden() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionService.requireMember(SPACE_ID));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void shouldRejectWhenRoleBelowRequired() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member(SpaceRole.VIEWER));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionService.requireRole(SPACE_ID, SpaceRole.EDITOR));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void shouldPassWhenRoleMeetsRequired() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member(SpaceRole.OWNER));
        assertEquals(SpaceRole.OWNER, permissionService.requireRole(SPACE_ID, SpaceRole.EDITOR));
        // EDITOR 满足 VIEWER 要求
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member(SpaceRole.EDITOR));
        assertEquals(SpaceRole.EDITOR, permissionService.requireRole(SPACE_ID, SpaceRole.VIEWER));
    }

    @Test
    void shouldRejectWhenNotLoggedIn() {
        SecurityContextHolder.clearContext();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionService.requireMember(SPACE_ID));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }
}
