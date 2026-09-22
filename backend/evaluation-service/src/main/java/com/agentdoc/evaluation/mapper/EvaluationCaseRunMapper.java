package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.EvaluationCaseRunEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EvaluationCaseRunMapper extends BaseMapper<EvaluationCaseRunEntity> {
    int insertBatch(@Param("entities") List<EvaluationCaseRunEntity> entities);

    int updateBatch(@Param("entities") List<EvaluationCaseRunEntity> entities);
}
