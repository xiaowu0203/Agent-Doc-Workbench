package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.SkillEntity;
import com.agentdoc.agent.pojo.vo.SkillBindingCountVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import java.util.Collection;
import java.util.List;

public interface SkillMapper extends BaseMapper<SkillEntity> {
    List<SkillBindingCountVO> selectEnabledAgentCounts(@Param("skillIds") Collection<Long> skillIds);

    List<SkillBindingCountVO> selectEnabledAgentCountsInSpace(@Param("spaceId") Long spaceId,
                                                               @Param("skillIds") Collection<Long> skillIds);

    Page<SkillEntity> selectVisibleInSpacePage(Page<SkillEntity> page,
                                                @Param("spaceId") Long spaceId,
                                                @Param("status") Integer status,
                                                @Param("sourceType") String sourceType,
                                                @Param("keyword") String keyword);
}
