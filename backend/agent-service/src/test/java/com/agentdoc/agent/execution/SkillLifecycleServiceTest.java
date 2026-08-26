package com.agentdoc.agent.execution;

import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.service.SkillAuditLogService;
import com.agentdoc.agent.service.SkillService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.DocumentFeign;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillLifecycleServiceTest {

    @Test
    void reservesNextVersionAndPersistsIncrement() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillEntity skill = new SkillEntity();
        skill.setId(7L);
        skill.setSpaceId(9L);
        skill.setStatus(SkillStatus.ACTIVE.getCode());
        skill.setNextVersionNo(4);
        when(skillMapper.selectOne(any())).thenReturn(skill);
        SkillService service = new SkillService(skillMapper, mock(SkillVersionMapper.class),
                permittedDocumentFeign(), mock(SkillAuditLogService.class));

        assertThat(service.reserveVersionNo(7L)).isEqualTo(4);
        assertThat(skill.getNextVersionNo()).isEqualTo(5);
        verify(skillMapper).updateById(skill);
    }

    @Test
    void disabledSkillCannotReserveVersion() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillEntity skill = new SkillEntity();
        skill.setId(7L);
        skill.setSpaceId(9L);
        skill.setStatus(SkillStatus.DISABLED.getCode());
        when(skillMapper.selectOne(any())).thenReturn(skill);
        SkillService service = new SkillService(skillMapper, mock(SkillVersionMapper.class),
                permittedDocumentFeign(), mock(SkillAuditLogService.class));

        assertThatThrownBy(() -> service.reserveVersionNo(7L))
                .hasMessageContaining("停用");
    }

    private DocumentFeign permittedDocumentFeign() {
        DocumentFeign documentFeign = mock(DocumentFeign.class);
        when(documentFeign.checkSpacePermission(any(), any())).thenReturn(Result.ok());
        return documentFeign;
    }
}
