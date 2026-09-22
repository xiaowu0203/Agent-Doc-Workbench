package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.AgentTemplateMcpEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentTemplateMcpMapper extends BaseMapper<AgentTemplateMcpEntity> {
    int insertBatch(@Param("references") List<AgentTemplateMcpEntity> references);
}
