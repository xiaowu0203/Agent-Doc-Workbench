package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.vo.SkillBindingCountVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import java.util.Collection;
import java.util.List;

public interface SkillMapper extends BaseMapper<SkillEntity> {
    List<SkillBindingCountVO> selectEnabledAgentCounts(@Param("skillIds") Collection<Long> skillIds);
}
