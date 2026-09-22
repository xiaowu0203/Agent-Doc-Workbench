package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.AgentSkillEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AgentSkillMapper extends BaseMapper<AgentSkillEntity> {
    int updateBatch(@Param("bindings") List<AgentSkillEntity> bindings);

    int insertBatch(@Param("bindings") List<AgentSkillEntity> bindings);

    List<Long> selectEnabledAgentIdsInSpace(@Param("spaceId") Long spaceId,
                                            @Param("skillId") Long skillId);

    int updateEnabledSkillVersionInSpace(@Param("spaceId") Long spaceId,
                                         @Param("skillId") Long skillId,
                                         @Param("skillVersionId") Long skillVersionId);
}
