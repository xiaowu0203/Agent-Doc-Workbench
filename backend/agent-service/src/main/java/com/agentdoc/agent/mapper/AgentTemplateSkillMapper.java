package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.AgentTemplateSkillEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentTemplateSkillMapper extends BaseMapper<AgentTemplateSkillEntity> {
    int insertBatch(@Param("references") List<AgentTemplateSkillEntity> references);
}
