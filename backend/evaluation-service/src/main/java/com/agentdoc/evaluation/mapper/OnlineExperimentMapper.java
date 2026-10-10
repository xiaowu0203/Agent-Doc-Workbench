package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.OnlineExperimentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

public interface OnlineExperimentMapper extends BaseMapper<OnlineExperimentEntity> {
    @Select("SELECT * FROM online_experiment WHERE id=#{id} FOR UPDATE")
    OnlineExperimentEntity lock(Long id);
}
