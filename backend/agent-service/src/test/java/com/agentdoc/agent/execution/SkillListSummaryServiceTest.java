package com.agentdoc.agent.execution;

import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.enums.SkillVersionStatus;
import com.agentdoc.agent.mapper.SkillMapper;
import com.agentdoc.agent.mapper.SkillVersionMapper;
import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.agentdoc.agent.pojo.param.SkillSearchParam;
import com.agentdoc.agent.pojo.vo.SkillBindingCountVO;
import com.agentdoc.agent.service.SkillAuditLogService;
import com.agentdoc.agent.service.SkillService;
import com.agentdoc.agent.service.SpaceAccessService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillListSummaryServiceTest {

    @Test
    void returnsLatestVersionAndBindingCountWithoutPerCardQueries() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillVersionMapper versionMapper = mock(SkillVersionMapper.class);
        SpaceAccessService spaceAccessService = mock(SpaceAccessService.class);
        SkillService service = new SkillService(skillMapper, versionMapper, spaceAccessService,
                mock(SkillAuditLogService.class));
        SkillEntity skill = new SkillEntity();
        skill.setId(11L);
        skill.setSpaceId(7L);
        skill.setName("document-review");
        skill.setDisplayName("文档审查");
        skill.setDescription("检查文档质量");
        skill.setStatus(SkillStatus.ACTIVE.getCode());
        Page<SkillEntity> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(skill));
        SkillVersionEntity first = version(21L, 11L, 1, SkillVersionStatus.PUBLISHED, "[\"mcp__one\"]");
        SkillVersionEntity latest = version(22L, 11L, 2, SkillVersionStatus.DRAFT,
                "[\"mcp__one\",\"mcp__two\"]");
        SkillBindingCountVO bindingCount = new SkillBindingCountVO();
        bindingCount.setSkillId(11L);
        bindingCount.setBoundAgentCount(3L);

        when(skillMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(versionMapper.selectList(any())).thenReturn(List.of(first, latest));
        when(skillMapper.selectEnabledAgentCounts(List.of(11L))).thenReturn(List.of(bindingCount));

        SkillSearchParam param = new SkillSearchParam();
        param.setSpaceId(7L);
        var result = service.list(param);

        assertThat(result.records()).hasSize(1);
        var item = result.records().getFirst();
        assertThat(item.versionCount()).isEqualTo(2);
        assertThat(item.boundAgentCount()).isEqualTo(3);
        assertThat(item.latestVersion().versionNo()).isEqualTo(2);
        assertThat(item.latestVersion().status()).isEqualTo(SkillVersionStatus.DRAFT);
        assertThat(item.latestVersion().allowedToolCount()).isEqualTo(2);
    }

    private SkillVersionEntity version(Long id, Long skillId, int versionNo, SkillVersionStatus status,
                                       String allowedToolsJson) {
        SkillVersionEntity version = new SkillVersionEntity();
        version.setId(id);
        version.setSkillId(skillId);
        version.setVersionNo(versionNo);
        version.setStatus(status.getCode());
        version.setActivationDescription("版本说明");
        version.setAllowedToolsJson(allowedToolsJson);
        version.setCreatedAt(LocalDateTime.now());
        return version;
    }
}
