package com.agentdoc.evaluation.mapper;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetCaseEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EvaluationDatasetCaseMapper extends BaseMapper<EvaluationDatasetCaseEntity> {
    int insertBatch(@Param("entities") List<EvaluationDatasetCaseEntity> entities);
}
