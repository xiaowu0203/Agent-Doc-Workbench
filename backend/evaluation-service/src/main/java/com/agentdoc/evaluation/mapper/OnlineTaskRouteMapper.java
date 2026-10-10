package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.OnlineTaskRouteEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OnlineTaskRouteMapper extends BaseMapper<OnlineTaskRouteEntity> {
    @Select("SELECT * FROM online_task_route WHERE id=#{id} FOR UPDATE")
    OnlineTaskRouteEntity lock(Long id);
}
