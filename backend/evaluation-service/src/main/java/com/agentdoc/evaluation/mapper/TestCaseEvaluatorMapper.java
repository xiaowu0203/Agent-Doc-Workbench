package com.agentdoc.evaluation.mapper;
import com.agentdoc.evaluation.pojo.entity.TestCaseEvaluatorEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TestCaseEvaluatorMapper extends BaseMapper<TestCaseEvaluatorEntity> {
    int insertBatch(@Param("entities") List<TestCaseEvaluatorEntity> entities);
}
