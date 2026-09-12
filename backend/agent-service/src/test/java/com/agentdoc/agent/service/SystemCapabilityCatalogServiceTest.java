package com.agentdoc.agent.service;

import com.agentdoc.agent.enums.SystemCapabilityType;
import com.agentdoc.agent.mapper.SystemCapabilityCatalogMapper;
import com.agentdoc.agent.pojo.param.SystemCapabilitySearchParam;
import com.agentdoc.agent.pojo.vo.SystemCapabilityTypeStatisticsVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemCapabilityCatalogServiceTest {

    @Test
    void normalUserOnlyQueriesPublishedEnabledCapabilities() {
        SystemCapabilityCatalogMapper mapper = mock(SystemCapabilityCatalogMapper.class);
        PlatformAccessService accessService = mock(PlatformAccessService.class);
        SystemCapabilityCatalogService service = new SystemCapabilityCatalogService(mapper, accessService);
        SystemCapabilitySearchParam param = searchParam();
        param.setType(SystemCapabilityType.MCP_TEMPLATE);
        param.setStatus(0);
        param.setKeyword("  search  ");
        when(mapper.selectCatalogPage("MCP_TEMPLATE", null, "search", true, 0L, 10))
                .thenReturn(List.of());
        when(mapper.countCatalog("MCP_TEMPLATE", null, "search", true)).thenReturn(0L);

        service.search(param);

        verify(mapper).selectCatalogPage("MCP_TEMPLATE", null, "search", true, 0L, 10);
        verify(mapper).countCatalog("MCP_TEMPLATE", null, "search", true);
    }

    @Test
    void managerCanQueryAllCapabilitiesByStatus() {
        SystemCapabilityCatalogMapper mapper = mock(SystemCapabilityCatalogMapper.class);
        PlatformAccessService accessService = mock(PlatformAccessService.class);
        SystemCapabilityCatalogService service = new SystemCapabilityCatalogService(mapper, accessService);
        SystemCapabilitySearchParam param = searchParam();
        param.setPageNum(2);
        param.setStatus(0);
        when(accessService.hasRole(SUPER_ADMIN)).thenReturn(true);
        when(mapper.selectCatalogPage(null, 0, null, false, 10L, 10)).thenReturn(List.of());
        when(mapper.countCatalog(null, 0, null, false)).thenReturn(0L);

        service.search(param);

        verify(mapper).selectCatalogPage(null, 0, null, false, 10L, 10);
    }

    @Test
    void statisticsAggregatesAllCapabilityTypes() {
        SystemCapabilityCatalogMapper mapper = mock(SystemCapabilityCatalogMapper.class);
        PlatformAccessService accessService = mock(PlatformAccessService.class);
        SystemCapabilityCatalogService service = new SystemCapabilityCatalogService(mapper, accessService);
        when(mapper.selectStatistics()).thenReturn(List.of(
                statistics(SystemCapabilityType.SKILL, 2, 1, 3, 4),
                statistics(SystemCapabilityType.AGENT_TEMPLATE, 5, 4, 6, 7),
                statistics(SystemCapabilityType.MCP_TEMPLATE, 8, 7, 9, 10)));

        var result = service.statistics();

        verify(accessService).requireRole(SUPER_ADMIN);
        assertThat(result.totalCount()).isEqualTo(15);
        assertThat(result.enabledCount()).isEqualTo(12);
        assertThat(result.publishedVersionCount()).isEqualTo(18);
        assertThat(result.installationCount()).isEqualTo(21);
        assertThat(result.byType()).hasSize(3);
    }

    private SystemCapabilitySearchParam searchParam() {
        return new SystemCapabilitySearchParam();
    }

    private SystemCapabilityTypeStatisticsVO statistics(SystemCapabilityType type, long total, long enabled,
                                                        long publishedVersions, long installations) {
        return new SystemCapabilityTypeStatisticsVO(type, total, enabled, publishedVersions, installations);
    }
}
