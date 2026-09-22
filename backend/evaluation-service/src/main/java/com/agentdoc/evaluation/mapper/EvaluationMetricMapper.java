package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EvaluationMetricMapper extends BaseMapper<EvaluationMetricEntity> {
    int insertBatch(@Param("entities") List<EvaluationMetricEntity> entities);
}
