package com.agentdoc.agent.service;

import com.agentdoc.agent.convertor.AgentConvertor;
import com.agentdoc.agent.enums.AgentStatus;
import com.agentdoc.agent.enums.SkillSelectionMode;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.AgentTemplateMapper;
import com.agentdoc.agent.mapper.AgentTemplateSkillMapper;
import com.agentdoc.agent.mapper.AgentTemplateVersionMapper;
import com.agentdoc.agent.pojo.dto.AgentTemplateUpgradeDTO;
import com.agentdoc.agent.pojo.dto.AgentTemplateInstallDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentTemplateEntity;
import com.agentdoc.agent.pojo.entity.AgentTemplateVersionEntity;
import com.agentdoc.agent.pojo.vo.AgentTemplateUpgradeVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTemplateServiceTest {

    @Test
    void previewsThreeWayConflictWithoutChangingAgent() {
        AgentTemplateMapper templateMapper = mock(AgentTemplateMapper.class);
        AgentTemplateVersionMapper versionMapper = mock(AgentTemplateVersionMapper.class);
        AgentTemplateSkillMapper skillReferenceMapper = mock(AgentTemplateSkillMapper.class);
        AgentService agentService = mock(AgentService.class);
        AgentSkillService agentSkillService = mock(AgentSkillService.class);
        AgentTemplateService service = new AgentTemplateService(templateMapper, versionMapper,
                skillReferenceMapper, mock(SpaceSkillInstallationService.class),
                mock(PlatformAccessService.class), mock(ModelService.class), agentService, agentSkillService,
                mock(SpaceAccessService.class), mock(SkillAuditLogService.class));

        AgentEntity agent = agent("space-custom-prompt");
        AgentTemplateEntity template = new AgentTemplateEntity();
        template.setId(10L);
        template.setStatus(AgentStatus.ENABLED.getCode());
        AgentTemplateVersionEntity current = version(100L, 1, "old-prompt");
        AgentTemplateVersionEntity target = version(200L, 2, "new-prompt");
        when(agentService.require(1L)).thenReturn(agent);
        when(templateMapper.selectById(10L)).thenReturn(template);
        when(versionMapper.selectById(100L)).thenReturn(current);
        when(versionMapper.selectById(200L)).thenReturn(target);
        when(skillReferenceMapper.selectList(any())).thenReturn(List.of());
        when(agentSkillService.listEnabledVersionIds(1L)).thenReturn(List.of());

        AgentTemplateUpgradeVO preview = service.upgrade(1L,
                new AgentTemplateUpgradeDTO(200L, true, null, null));

        assertThat(preview.applied()).isFalse();
        assertThat(preview.conflictingFields()).containsExactly("systemPrompt");
        assertThat(preview.proposedConfig().systemPrompt()).isEqualTo("space-custom-prompt");
        verify(agentService, never()).update(any(), any());
        verify(agentSkillService, never()).replace(any(), any());
    }

    @Test
    void installsPublishedTemplateAsIndependentSpaceAgent() {
        AgentTemplateMapper templateMapper = mock(AgentTemplateMapper.class);
        AgentTemplateVersionMapper versionMapper = mock(AgentTemplateVersionMapper.class);
        AgentTemplateSkillMapper skillReferenceMapper = mock(AgentTemplateSkillMapper.class);
        AgentService agentService = mock(AgentService.class);
        AgentSkillService agentSkillService = mock(AgentSkillService.class);
        AgentTemplateService service = new AgentTemplateService(templateMapper, versionMapper,
                skillReferenceMapper, mock(SpaceSkillInstallationService.class),
                mock(PlatformAccessService.class), mock(ModelService.class), agentService, agentSkillService,
                mock(SpaceAccessService.class), mock(SkillAuditLogService.class));
        AgentTemplateEntity template = new AgentTemplateEntity();
        template.setId(10L);
        template.setStatus(AgentStatus.ENABLED.getCode());
        AgentTemplateVersionEntity published = version(200L, 2, "published-prompt");
        AgentEntity created = agent("published-prompt");
        created.setTemplateId(null);
        created.setTemplateVersionId(null);
        when(versionMapper.selectById(200L)).thenReturn(published);
        when(templateMapper.selectById(10L)).thenReturn(template);
        when(skillReferenceMapper.selectList(any())).thenReturn(List.of());
        when(agentService.create(any())).thenReturn(AgentConvertor.toVO(created));
        when(agentService.requireForUpdate(1L)).thenReturn(created);
        when(agentService.detail(1L)).thenAnswer(ignored -> AgentConvertor.toVO(created));

        service.install(9L, new AgentTemplateInstallDTO(200L, "Space Agent", "[7]"));

        assertThat(created.getTemplateId()).isEqualTo(10L);
        assertThat(created.getTemplateVersionId()).isEqualTo(200L);
        verify(agentService).updateConfiguration(created);
        verify(agentSkillService, never()).replace(any(), any());
    }

    private AgentEntity agent(String prompt) {
        AgentEntity agent = new AgentEntity();
        agent.setId(1L);
        agent.setSpaceId(9L);
        agent.setTemplateId(10L);
        agent.setTemplateVersionId(100L);
        agent.setName("Template Agent");
        agent.setDescription("description");
        agent.setSystemPrompt(prompt);
        agent.setModelId(20L);
        agent.setSkillSelectionMode(SkillSelectionMode.ALL_BOUND.name());
        agent.setExternalMcpEnabled(false);
        agent.setMaxIterations(12);
        agent.setExecutionTimeoutSeconds(600);
        agent.setConfigVersion(1L);
        agent.setStatus(AgentStatus.ENABLED.getCode());
        return agent;
    }

    private AgentTemplateVersionEntity version(Long id, int versionNo, String prompt) {
        AgentTemplateVersionEntity version = new AgentTemplateVersionEntity();
        version.setId(id);
        version.setTemplateId(10L);
        version.setVersionNo(versionNo);
        version.setStatus(SkillVersionStatus.PUBLISHED.getCode());
        version.setDisplayName("Template Agent");
        version.setDescription("description");
        version.setSystemPrompt(prompt);
        version.setModelId(20L);
        version.setSkillSelectionMode(SkillSelectionMode.ALL_BOUND.name());
        version.setExternalMcpEnabled(false);
        version.setMaxIterations(12);
        version.setExecutionTimeoutSeconds(600);
        return version;
    }
}
