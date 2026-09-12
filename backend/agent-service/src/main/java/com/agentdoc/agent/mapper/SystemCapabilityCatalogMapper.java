package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.vo.SystemCapabilityCatalogVO;
import com.agentdoc.agent.pojo.vo.SystemCapabilityTypeStatisticsVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SystemCapabilityCatalogMapper {
    List<SystemCapabilityCatalogVO> selectCatalogPage(
            @Param("type") String type,
            @Param("status") Integer status,
            @Param("keyword") String keyword,
            @Param("publishedOnly") boolean publishedOnly,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize);

    long countCatalog(
            @Param("type") String type,
            @Param("status") Integer status,
            @Param("keyword") String keyword,
            @Param("publishedOnly") boolean publishedOnly);

    List<SystemCapabilityTypeStatisticsVO> selectStatistics();
}
