package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.SkillScopeType;
import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.mapper.AgentSkillMapper;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.mapper.SpaceSkillInstallationMapper;
import com.agentdoc.agent.pojo.dto.SpaceSkillInstallationUpdateDTO;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.entity.SpaceSkillInstallationEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_MANAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceSkillInstallationServiceTest {

    @Test
    void upgradeUpdatesBoundAgentsAndTheirConfigVersionsAtomically() {
        SpaceSkillInstallationMapper installationMapper = mock(SpaceSkillInstallationMapper.class);
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillVersionMapper versionMapper = mock(SkillVersionMapper.class);
        AgentSkillMapper agentSkillMapper = mock(AgentSkillMapper.class);
        AgentMapper agentMapper = mock(AgentMapper.class);
        SpaceAccessService spaceAccessService = mock(SpaceAccessService.class);
        SpaceSkillInstallationService service = new SpaceSkillInstallationService(installationMapper, skillMapper,
                versionMapper, agentSkillMapper, agentMapper, spaceAccessService, mock(SkillAuditLogService.class));

        SpaceSkillInstallationEntity installation = installation(5L, 9L, 7L, 21L);
        SkillEntity skill = systemSkill(7L);
        SkillVersionEntity version = publishedVersion(22L, 7L, 2);
        when(installationMapper.selectOne(any())).thenReturn(installation);
        when(skillMapper.selectById(7L)).thenReturn(skill);
        when(versionMapper.selectById(22L)).thenReturn(version);
        when(agentSkillMapper.selectEnabledAgentIdsInSpace(9L, 7L)).thenReturn(List.of(100L, 200L));
        when(agentSkillMapper.updateEnabledSkillVersionInSpace(9L, 7L, 22L)).thenReturn(2);
        when(skillMapper.selectBatchIds(anyCollection())).thenReturn(List.of(skill));
        when(versionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(version));
        when(versionMapper.selectList(any())).thenReturn(List.of(version));

        var result = service.update(9L, 5L, new SpaceSkillInstallationUpdateDTO(22L, null));

        assertThat(result.skillVersionId()).isEqualTo(22L);
        verify(spaceAccessService).requirePermission(9L, SKILL_MANAGE);
        verify(agentMapper).incrementConfigVersions(List.of(100L, 200L));
        verify(installationMapper).updateById(installation);
    }

    @Test
    void refusesToUninstallWhileAnEnabledAgentBindingExists() {
        SpaceSkillInstallationMapper installationMapper = mock(SpaceSkillInstallationMapper.class);
        AgentSkillMapper agentSkillMapper = mock(AgentSkillMapper.class);
        SpaceSkillInstallationService service = new SpaceSkillInstallationService(installationMapper,
                mock(SkillMapper.class), mock(SkillVersionMapper.class), agentSkillMapper,
                mock(AgentMapper.class), mock(SpaceAccessService.class), mock(SkillAuditLogService.class));
        when(installationMapper.selectOne(any())).thenReturn(installation(5L, 9L, 7L, 21L));
        when(agentSkillMapper.selectEnabledAgentIdsInSpace(9L, 7L)).thenReturn(List.of(100L));

        assertThatThrownBy(() -> service.uninstall(9L, 5L))
                .hasMessageContaining("仍有空间 Agent 绑定");
    }

    @Test
    void rejectsSystemSkillWhenInstalledVersionDoesNotMatch() {
        SpaceSkillInstallationMapper installationMapper = mock(SpaceSkillInstallationMapper.class);
        SpaceSkillInstallationService service = new SpaceSkillInstallationService(installationMapper,
                mock(SkillMapper.class), mock(SkillVersionMapper.class), mock(AgentSkillMapper.class),
                mock(AgentMapper.class), mock(SpaceAccessService.class), mock(SkillAuditLogService.class));
        when(installationMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.requireEnabledInstallation(9L, 7L, 22L))
                .hasMessageContaining("版本不匹配");
    }

    private SpaceSkillInstallationEntity installation(long id, long spaceId, long skillId, long versionId) {
        SpaceSkillInstallationEntity entity = new SpaceSkillInstallationEntity();
        entity.setId(id);
        entity.setSpaceId(spaceId);
        entity.setSkillId(skillId);
        entity.setSkillVersionId(versionId);
        entity.setEnabled(true);
        entity.setInstalledBy(1L);
        return entity;
    }

    private SkillEntity systemSkill(long id) {
        SkillEntity skill = new SkillEntity();
        skill.setId(id);
        skill.setScopeType(SkillScopeType.SYSTEM.name());
        skill.setName("system-skill");
        skill.setDisplayName("系统 Skill");
        skill.setDescription("description");
        skill.setStatus(SkillStatus.ACTIVE.getCode());
        return skill;
    }

    private SkillVersionEntity publishedVersion(long id, long skillId, int versionNo) {
        SkillVersionEntity version = new SkillVersionEntity();
        version.setId(id);
        version.setSkillId(skillId);
        version.setVersionNo(versionNo);
        version.setStatus(SkillVersionStatus.PUBLISHED.getCode());
        return version;
    }
}
