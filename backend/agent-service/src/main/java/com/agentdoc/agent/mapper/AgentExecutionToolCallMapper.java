package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.AgentExecutionToolCallEntity;
import com.agentdoc.agent.pojo.vo.ToolSourceCountRow;
import com.agentdoc.common.feign.dto.AgentToolUsageQueryDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentExecutionToolCallMapper extends BaseMapper<AgentExecutionToolCallEntity> {

    List<ToolSourceCountRow> aggregateBySource(@Param("query") AgentToolUsageQueryDTO query);
}
