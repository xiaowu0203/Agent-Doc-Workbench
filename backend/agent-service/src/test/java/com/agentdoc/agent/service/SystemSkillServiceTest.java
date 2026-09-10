package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.SkillScopeType;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.dto.SystemSkillCreateDTO;
import com.agentdoc.common.constant.JwtConstant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemSkillServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsSystemSkillWithoutSpaceOwnership() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        PlatformAccessService platformAccessService = mock(PlatformAccessService.class);
        SkillService service = new SkillService(skillMapper, mock(SkillVersionMapper.class),
                mock(SpaceAccessService.class), platformAccessService, mock(SkillAuditLogService.class));
        when(skillMapper.selectCount(any())).thenReturn(0L);
        login(1001L);

        var created = service.createSystem(new SystemSkillCreateDTO(
                "document-review", "文档审查", "审查文档结构与内容"));

        assertThat(created.getScopeType()).isEqualTo(SkillScopeType.SYSTEM.name());
        assertThat(created.getSpaceId()).isNull();
        assertThat(created.getCreatedBy()).isEqualTo(1001L);
        verify(platformAccessService).requireRole(SUPER_ADMIN);
        verify(skillMapper).insert(created);
    }

    private void login(long userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(userId))
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER)
                .claim(JwtConstant.CLAIM_PLATFORM_ROLES, List.of(SUPER_ADMIN))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
