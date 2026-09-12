package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.SystemCapabilityCatalogMapper;
import com.agentdoc.agent.pojo.param.SystemCapabilitySearchParam;
import com.agentdoc.agent.pojo.vo.SystemCapabilityCatalogVO;
import com.agentdoc.agent.pojo.vo.SystemCapabilityStatisticsVO;
import com.agentdoc.agent.pojo.vo.SystemCapabilityTypeStatisticsVO;
import com.agentdoc.common.pojo.vo.PageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

@Service
@RequiredArgsConstructor
public class SystemCapabilityCatalogService {
    private final SystemCapabilityCatalogMapper mapper;
    private final PlatformAccessService platformAccessService;

    /** 统一分页查询系统 Skill、Agent 模板和 MCP 模板。 */
    public PageVO<SystemCapabilityCatalogVO> search(SystemCapabilitySearchParam param) {
        param.validate();
        boolean manager = platformAccessService.hasRole(SUPER_ADMIN);
        String type = param.getType() == null ? null : param.getType().name();
        Integer status = manager ? param.getStatus() : null;
        String keyword = normalizeKeyword(param.getKeyword());
        long offset = (long) (param.getPageNum() - 1) * param.getPageSize();
        List<SystemCapabilityCatalogVO> records = mapper.selectCatalogPage(
                type, status, keyword, !manager, offset, param.getPageSize());
        long total = mapper.countCatalog(type, status, keyword, !manager);
        return PageVO.of(records, total, param);
    }

    /** 查询平台全量统计，仅平台超级管理员可访问。 */
    public SystemCapabilityStatisticsVO statistics() {
        platformAccessService.requireRole(SUPER_ADMIN);
        List<SystemCapabilityTypeStatisticsVO> byType = mapper.selectStatistics();
        return new SystemCapabilityStatisticsVO(
                sum(byType, SystemCapabilityTypeStatisticsVO::totalCount),
                sum(byType, SystemCapabilityTypeStatisticsVO::enabledCount),
                sum(byType, SystemCapabilityTypeStatisticsVO::publishedVersionCount),
                sum(byType, SystemCapabilityTypeStatisticsVO::installationCount),
                List.copyOf(byType));
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private long sum(List<SystemCapabilityTypeStatisticsVO> values,
                     Function<SystemCapabilityTypeStatisticsVO, Long> getter) {
        return values.stream().map(getter).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }
}
