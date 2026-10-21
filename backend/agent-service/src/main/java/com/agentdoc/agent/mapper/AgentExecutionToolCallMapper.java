package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.AgentExecutionToolCallEntity;
import com.agentdoc.agent.pojo.vo.ToolSourceCountRow;
import com.agentdoc.common.feign.dto.AgentToolUsageQueryDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentExecutionToolCallMapper extends BaseMapper<AgentExecutionToolCallEntity> {

    List<ToolSourceCountRow> aggregateBySource(@Param("query") AgentToolUsageQueryDTO query);
    Page<AgentExecutionToolCallEntity> search(Page<AgentExecutionToolCallEntity> page, @Param("query") AgentToolUsageQueryDTO query);
}
