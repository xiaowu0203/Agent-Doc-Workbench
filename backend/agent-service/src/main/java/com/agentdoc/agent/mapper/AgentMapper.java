package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.vo.ModelAgentCountVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

public interface AgentMapper extends BaseMapper<AgentEntity> {
    List<ModelAgentCountVO> selectModelAgentCounts(@Param("modelIds") Collection<Long> modelIds);

    int incrementConfigVersions(@Param("agentIds") Collection<Long> agentIds);
}
