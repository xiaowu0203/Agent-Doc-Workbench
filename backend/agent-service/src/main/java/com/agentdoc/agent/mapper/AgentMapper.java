package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.AgentEntity;
import com.agentdoc.agent.pojo.vo.ModelAgentCountVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface AgentMapper extends BaseMapper<AgentEntity> {

    @Select({
            "<script>",
            "SELECT model_ref.model_id AS modelId, COUNT(DISTINCT model_ref.agent_id) AS agentCount",
            "FROM (",
            "SELECT id AS agent_id, model_id FROM agent",
            "WHERE deleted = 0 AND model_id IN",
            "<foreach collection='modelIds' item='modelId' open='(' separator=',' close=')'>",
            "#{modelId}",
            "</foreach>",
            "UNION ALL",
            "SELECT id AS agent_id, skill_router_model_id AS model_id FROM agent",
            "WHERE deleted = 0 AND skill_router_model_id IN",
            "<foreach collection='modelIds' item='modelId' open='(' separator=',' close=')'>",
            "#{modelId}",
            "</foreach>",
            ") model_ref",
            "GROUP BY model_ref.model_id",
            "</script>"
    })
    List<ModelAgentCountVO> selectModelAgentCounts(@Param("modelIds") Collection<Long> modelIds);
}
