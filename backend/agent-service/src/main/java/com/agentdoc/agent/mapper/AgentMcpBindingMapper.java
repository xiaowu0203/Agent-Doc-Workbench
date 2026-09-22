package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.AgentMcpBindingEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentMcpBindingMapper extends BaseMapper<AgentMcpBindingEntity> {
    int updateBatch(@Param("bindings") List<AgentMcpBindingEntity> bindings);

    int insertBatch(@Param("bindings") List<AgentMcpBindingEntity> bindings);
}
