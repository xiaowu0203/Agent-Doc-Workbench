package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.vo.SkillBindingCountVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface SkillMapper extends BaseMapper<SkillEntity> {

    @Select({
            "<script>",
            "SELECT relation.skill_id AS skillId, COUNT(DISTINCT relation.agent_id) AS boundAgentCount",
            "FROM agent_skill relation",
            "INNER JOIN agent ON agent.id = relation.agent_id AND agent.deleted = 0",
            "WHERE relation.enabled = 1 AND relation.skill_id IN",
            "<foreach collection='skillIds' item='skillId' open='(' separator=',' close=')'>",
            "#{skillId}",
            "</foreach>",
            "GROUP BY relation.skill_id",
            "</script>"
    })
    List<SkillBindingCountVO> selectEnabledAgentCounts(@Param("skillIds") Collection<Long> skillIds);
}
