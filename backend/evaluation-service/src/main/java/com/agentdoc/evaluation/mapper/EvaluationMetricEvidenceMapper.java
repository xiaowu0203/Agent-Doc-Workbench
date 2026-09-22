package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.EvaluationMetricEvidenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EvaluationMetricEvidenceMapper extends BaseMapper<EvaluationMetricEvidenceEntity> {
    int insertBatch(@Param("entities") List<EvaluationMetricEvidenceEntity> entities);
}
