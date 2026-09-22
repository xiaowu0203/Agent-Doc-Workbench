package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.EvaluationCaseAttemptEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EvaluationCaseAttemptMapper extends BaseMapper<EvaluationCaseAttemptEntity> {
    int insertBatch(@Param("entities") List<EvaluationCaseAttemptEntity> entities);

    int updateBatch(@Param("entities") List<EvaluationCaseAttemptEntity> entities);
}
