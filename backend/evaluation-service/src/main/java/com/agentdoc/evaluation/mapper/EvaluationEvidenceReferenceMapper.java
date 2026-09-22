package com.agentdoc.evaluation.mapper;

import com.agentdoc.evaluation.pojo.entity.EvaluationEvidenceReferenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EvaluationEvidenceReferenceMapper extends BaseMapper<EvaluationEvidenceReferenceEntity> {
    int insertBatch(@Param("entities") List<EvaluationEvidenceReferenceEntity> entities);
}
