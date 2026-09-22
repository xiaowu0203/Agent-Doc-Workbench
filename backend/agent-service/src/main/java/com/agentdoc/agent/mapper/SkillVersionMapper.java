package com.agentdoc.agent.mapper;

import com.agentdoc.agent.pojo.entity.SkillVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

public interface SkillVersionMapper extends BaseMapper<SkillVersionEntity> {
    List<String> selectReferencedStorageKeys();
}
