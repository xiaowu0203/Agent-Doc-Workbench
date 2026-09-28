package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.ExperimentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ExperimentMapper extends BaseMapper<ExperimentEntity> {
    @Select("SELECT e.* FROM experiment e WHERE e.status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS') "
            + "AND NOT EXISTS (SELECT 1 FROM experiment_report r WHERE r.experiment_id = e.id) "
            + "ORDER BY e.updated_at, e.id LIMIT #{limit}")
    List<ExperimentEntity> selectAwaitingInitialReport(@Param("limit") int limit);
}
