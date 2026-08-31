package com.agentdoc.agent.execution;

import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.AgentMapper;
import com.agentdoc.agent.mapper.AgentSkillMapper;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.dto.AgentSkillReplaceDTO;
import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.entity.AgentSkillEntity;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.service.AgentService;
import com.agentdoc.agent.service.AgentSkillService;
import com.agentdoc.agent.service.SkillAuditLogService;
import com.agentdoc.agent.service.SkillService;
import com.agentdoc.agent.service.SpaceAccessService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_BIND_SKILL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSkillServiceTest {

    @Test
    void unchangedBindingDoesNotIncrementConfigVersion() {
        AgentService agentService = mock(AgentService.class);
        SkillService skillService = mock(SkillService.class);
        SpaceAccessService spaceAccessService = mock(SpaceAccessService.class);
        AgentMapper agentMapper = mock(AgentMapper.class);
        AgentSkillMapper agentSkillMapper = mock(AgentSkillMapper.class);
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillVersionMapper versionMapper = mock(SkillVersionMapper.class);
        SkillAuditLogService auditLogService = mock(SkillAuditLogService.class);
        AgentSkillService service = new AgentSkillService(agentService, skillService, spaceAccessService, agentMapper,
                agentSkillMapper, skillMapper, versionMapper, auditLogService);

        AgentEntity agent = new AgentEntity();
        agent.setId(10L);
        agent.setSpaceId(20L);
        agent.setConfigVersion(3L);
        SkillEntity skill = new SkillEntity();
        skill.setId(30L);
        skill.setSpaceId(20L);
        skill.setStatus(SkillStatus.ACTIVE.getCode());
        SkillVersionEntity version = new SkillVersionEntity();
        version.setId(40L);
        version.setSkillId(30L);
        version.setVersionNo(1);
        version.setStatus(SkillVersionStatus.PUBLISHED.getCode());
        List<AgentSkillEntity> bindings = new ArrayList<>();

        when(agentMapper.selectOne(any())).thenReturn(agent);
        when(versionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(version));
        when(skillMapper.selectBatchIds(anyCollection())).thenReturn(List.of(skill));
        when(agentSkillMapper.selectList(any())).thenAnswer(invocation -> bindings);
        doAnswer(invocation -> {
            bindings.add(invocation.getArgument(0));
            return 1;
        }).when(agentSkillMapper).insert(any(AgentSkillEntity.class));
        doNothing().when(spaceAccessService).requirePermission(20L, AGENT_BIND_SKILL);

        AgentSkillReplaceDTO request = new AgentSkillReplaceDTO(List.of(40L));
        service.replace(10L, request);
        service.replace(10L, request);

        assertThat(agent.getConfigVersion()).isEqualTo(4L);
        verify(agentMapper, times(1)).updateById(agent);
        verify(auditLogService, times(1)).record(any(), any(), any(), any(), any());
    }
}
